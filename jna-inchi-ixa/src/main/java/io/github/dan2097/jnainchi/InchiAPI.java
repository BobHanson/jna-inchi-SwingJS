/**
 * JNA-InChI - Library for calling InChI from Java
 * Copyright © 2018 Daniel Lowe
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.dan2097.jnainchi;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.sun.jna.Platform;
import com.sun.jna.Pointer;

import io.github.dan2097.jnainchi.inchi.IXA;
import io.github.dan2097.jnainchi.inchi.InchiLibrary;
import io.github.dan2097.jnainchi.inchi.InchiLibrary.IXA_BOND_WEDGE;
import io.github.dan2097.jnainchi.inchi.InchiLibrary.IXA_DBLBOND_CONFIG;
import io.github.dan2097.jnainchi.inchi.InchiLibrary.IXA_INCHIBUILDER_OPTION;
import io.github.dan2097.jnainchi.inchi.InchiLibrary.IXA_INCHIBUILDER_STEREOOPTION;

/**
 * This class interacts with c or WASM native code to access the InChI Extended
 * Application Programming Interface. It utilizes INCHI_API libinchi/IXA_*
 * methods along with selected "JavaScript-safe" functions within inchi C. It
 * works for both WASM for JavaScript and for JNA for Java with almost no
 * changes in the code. (Look for references to the field "isJS".
 * <p>
 * This implementation does not have to interpret architecture-specific
 * alignments of structures because all of the returned values from these c
 * functions are numbers or strings. All of the objects of inchi c -- molecules,
 * atoms, bonds, stereocenter metadata, builders, etc. -- are returned only as
 * shared memory address pointers. These are Pointer objects that hold a single
 * int value in Java and just simple int32 in JavaScript. (The Pointer
 * parameters and return values in the native calls of InchiLibrary are taken
 * care of by the java2script transpiler, converting them to and from the int
 * JavaScript values.)
 * <p>
 * JNA-InChI's JnaInchi and InchiFunctions java classes both add another layer
 * of Pointer subclasses that allows distinguishing these pointers from one
 * another type, exactly in the way that is done in inchi C. As clever as this
 * is, annd while this makes good sense, it's really not necessary, and it adds
 * just another layer of complexity and object creation every time a native call
 * is made. To improve performance, I have opted here to use just the raw
 * com.sun.jna.Pointer for these here. This does not detract at all from the
 * functionality and (I think) makes the code a bit more readable.
 * <p>
 * You might notice that in this class there is no run-time reference to
 * com.sun.jna. (Pointer references are only at compile time; the only reference
 * is a compile-time final static String providing the name of the architecture.
 * No references at all to com.sun.jna.Pointer, Structure, or Library. Just a
 * passing reference to com.sun.jna.Platform. Instead, all the pointers are
 * simply left as Pointer. This greatly simplifies the function signatures in
 * JavaScript and should note be any particular issue in Java, either. The
 * pointers are only used fleetingly to get an atom ID, for example, so that it
 * can be passed back to another IXA function.
 * <p>
 * This is in contrast to the original JNA-InChI, which generates template Java
 * classes from C strutures and then populates them on the fly. This is not
 * something that is available in WASM. (Not that it is impossible, but you
 * really do not want to mess with converting structures in C to anything in
 * Java or JavaScript. If you want to see how involved this is, take a peek at
 * com.sun.jna.Structure.java .)
 * <p>
 * The following libinchi methods that don't start with "IXA_" but are still
 * part of this package because they are JavaScript-safe include:
 * 
 * <pre>
 InchiLibrary.CheckINCHI
 InchiLibrary.CheckINCHIKey
 InchiLibrary.GetINCHIKeyFromINCHI
 * </pre>
 * 
 * The following libinchi methods are not part of this package:
 * 
 * <pre>
Free_inchi_Input
Free_std_inchi_Input
Get_inchi_Input_FromAuxInfo
Get_std_inchi_Input_FromAuxInfo
GetINCHIfromINCHI  (replaced with IXA_MOL_ReadInChI)
GetStdINCHIKeyFromStdINCHI
INCHIGEN_Create
INCHIGEN_Destroy
INCHIGEN_DoCanonicalization
INCHIGEN_DoNormalization
INCHIGEN_DoSerialization
INCHIGEN_Reset
INCHIGEN_Setup
MakeINCHIFromMolfileText (replaced with IXA.IXA_MOL_ReadMolfile)
STDINCHIGEN_Create
STDINCHIGEN_Destroy
STDINCHIGEN_DoCanonicalization
STDINCHIGEN_DoNormalization
STDINCHIGEN_DoSerialization
STDINCHIGEN_Reset
STDINCHIGEN_Setup
 * </pre>
 * 
 * The development of this class was a day's exercise. An experiment to see if
 * we really could have a common interface for Java and JavaScript. In the end,
 * there were just one hitch. Currently there is no IXA access to (ichilnct.c)
 * Get_inchi_Input_FromAuxInfo. It would be good to have an IXA interface to this method.
 * 
 * The source is based on jnainchi.java, by Daniel Lowe.
 * 
 * @author Bob Hanson
 *
 */
@SuppressWarnings("boxing")
public class InchiAPI {

	private static boolean isJS = (/** @j2sNative true || */
	false);
	private static Throwable libraryLoadingError = null;
	private static String inchiLibName;
	static {
		try {
			// trigger a static load of InchiLibrary
			inchiLibName = InchiLibrary.JNA_NATIVE_LIB.getName();
		} catch (Throwable e) {
			e.printStackTrace();
			libraryLoadingError = e;
		}
	}

	/**
	 * For JavaScript, wait to continue until WASM loading has completed.
	 * 
	 * For Java, just run the Runnable.
	 * 
	 * This JavaScript implementation requires SwingjS.
	 * 
	 * @param r The runnable to run when ready, in Java or JavaScript.
	 * 
	 */
	public static void initAndRun(Runnable r) {
		@SuppressWarnings("unused")
		String wasmName = inchiLibName;
		/**
		 * In JavaScript, we wait for the WASM module to load.
		 * 
		 * @j2sNative
		 *    
		 *    if (!J2S) {
		 *      alert("J2S has not been installed");
		 *      System.exit(0);
		 *    }
		 *   var t = [];
		 *   t[0] = setInterval(
		 *      function(){
		 *       if (J2S.wasm[wasmName]) {
		 *        clearInterval(t[0]);
		 *        System.out.println("InChI WASM initialized successfully");
		 *        r.run$();
		 *       }
		 *        System.out.println("InChI WASM initializing...");
		 *      }, 50);
		 */ {
			 // in Java, no asynchronous issue
			 r.run();
		 }
	}

	private static void checkLibrary() {
		if (libraryLoadingError != null) {
			String platform = (isJS ? "WASM" : Platform.RESOURCE_PREFIX);
			throw new RuntimeException(
					"Error loading InChI native code. Please check that the binaries for your platform (" + platform
							+ ") have been included on the classpath.",
					libraryLoadingError);
		}
	}


	private static final int ISOTOPIC_SHIFT_RANGE_MIN = InchiLibrary.ISOTOPIC_SHIFT_FLAG
			- InchiLibrary.ISOTOPIC_SHIFT_MAX;
	private static final int ISOTOPIC_SHIFT_RANGE_MAX = InchiLibrary.ISOTOPIC_SHIFT_FLAG
			+ InchiLibrary.ISOTOPIC_SHIFT_MAX;
	private static final Map<String, Integer> aveMass = new HashMap<>();

	static {

		// avg mw from util.c

		aveMass.put("H", 1);
		aveMass.put("D", 2);
		aveMass.put("T", 3);
		aveMass.put("He", 4);
		aveMass.put("Li", 7);
		aveMass.put("Be", 9);
		aveMass.put("B", 11);
		aveMass.put("C", 12);
		aveMass.put("N", 14);
		aveMass.put("O", 16);
		aveMass.put("F", 19);
		aveMass.put("Ne", 20);
		aveMass.put("Na", 23);
		aveMass.put("Mg", 24);
		aveMass.put("Al", 27);
		aveMass.put("Si", 28);
		aveMass.put("P", 31);
		aveMass.put("S", 32);
		aveMass.put("Cl", 35);
		aveMass.put("Ar", 40);
		aveMass.put("K", 39);
		aveMass.put("Ca", 40);
		aveMass.put("Sc", 45);
		aveMass.put("Ti", 48);
		aveMass.put("V", 51);
		aveMass.put("Cr", 52);
		aveMass.put("Mn", 55);
		aveMass.put("Fe", 56);
		aveMass.put("Co", 59);
		aveMass.put("Ni", 59);
		aveMass.put("Cu", 64);
		aveMass.put("Zn", 65);
		aveMass.put("Ga", 70);
		aveMass.put("Ge", 73);
		aveMass.put("As", 75);
		aveMass.put("Se", 79);
		aveMass.put("Br", 80);
		aveMass.put("Kr", 84);
		aveMass.put("Rb", 85);
		aveMass.put("Sr", 88);
		aveMass.put("Y", 89);
		aveMass.put("Zr", 91);
		aveMass.put("Nb", 93);
		aveMass.put("Mo", 96);
		aveMass.put("Tc", 98);
		aveMass.put("Ru", 101);
		aveMass.put("Rh", 103);
		aveMass.put("Pd", 106);
		aveMass.put("Ag", 108);
		aveMass.put("Cd", 112);
		aveMass.put("In", 115);
		aveMass.put("Sn", 119);
		aveMass.put("Sb", 122);
		aveMass.put("Te", 128);
		aveMass.put("I", 127);
		aveMass.put("Xe", 131);
		aveMass.put("Cs", 133);
		aveMass.put("Ba", 137);
		aveMass.put("La", 139);
		aveMass.put("Ce", 140);
		aveMass.put("Pr", 141);
		aveMass.put("Nd", 144);
		aveMass.put("Pm", 145);
		aveMass.put("Sm", 150);
		aveMass.put("Eu", 152);
		aveMass.put("Gd", 157);
		aveMass.put("Tb", 159);
		aveMass.put("Dy", 163);
		aveMass.put("Ho", 165);
		aveMass.put("Er", 167);
		aveMass.put("Tm", 169);
		aveMass.put("Yb", 173);
		aveMass.put("Lu", 175);
		aveMass.put("Hf", 178);
		aveMass.put("Ta", 181);
		aveMass.put("W", 184);
		aveMass.put("Re", 186);
		aveMass.put("Os", 190);
		aveMass.put("Ir", 192);
		aveMass.put("Pt", 195);
		aveMass.put("Au", 197);
		aveMass.put("Hg", 201);
		aveMass.put("Tl", 204);
		aveMass.put("Pb", 207);
		aveMass.put("Bi", 209);
		aveMass.put("Po", 209);
		aveMass.put("At", 210);
		aveMass.put("Rn", 222);
		aveMass.put("Fr", 223);
		aveMass.put("Ra", 226);
		aveMass.put("Ac", 227);
		aveMass.put("Th", 232);
		aveMass.put("Pa", 231);
		aveMass.put("U", 238);
		aveMass.put("Np", 237);
		aveMass.put("Pu", 244);
		aveMass.put("Am", 243);
		aveMass.put("Cm", 247);
		aveMass.put("Bk", 247);
		aveMass.put("Cf", 251);
		aveMass.put("Es", 252);
		aveMass.put("Fm", 257);
		aveMass.put("Md", 258);
		aveMass.put("No", 259);
		aveMass.put("Lr", 260);
		aveMass.put("Rf", 261);
		aveMass.put("Db", 270);
		aveMass.put("Sg", 269);
		aveMass.put("Bh", 270);
		aveMass.put("Hs", 270);
		aveMass.put("Mt", 278);
		aveMass.put("Ds", 281);
		aveMass.put("Rg", 281);
		aveMass.put("Cn", 285);
		aveMass.put("Nh", 278);
		aveMass.put("Fl", 289);
		aveMass.put("Mc", 289);
		aveMass.put("Lv", 293);
		aveMass.put("Ts", 297);
		aveMass.put("Og", 294);
	}

	public static InchiOutput toInchi(InchiInput inchiInput) {
		return toInchi(inchiInput, InchiOptions.DEFAULT_OPTIONS);
	}

	public static InchiOutput toInchi(InchiInput inchiInput, InchiOptions options) {
		checkLibrary();
		List<InchiAtom> atoms = inchiInput.getAtoms();
		int atomCount = atoms.size();
		if (atomCount > Short.MAX_VALUE) {
			throw new IllegalStateException("InChI is limited to 32767 atoms, input contained " + atomCount + " atoms");
		}
		List<InchiBond> bonds = inchiInput.getBonds();
		List<InchiStereo> stereos = inchiInput.getStereos();
		if (stereos.size() > Short.MAX_VALUE) {
			throw new IllegalStateException("Too many stereochemistry elements in input");
		}

		Pointer hStatus = IXA.IXA_STATUS_Create();
		Pointer nativeMol = IXA.IXA_MOL_Create(hStatus);
		IXA.IXA_MOL_ReserveSpace(hStatus, nativeMol, atomCount, bonds.size(), stereos.size());
		try {
			Map<InchiAtom, Pointer> atomToNativeAtom = addAtoms(nativeMol, hStatus, atoms);
			addBonds(nativeMol, hStatus, bonds, atomToNativeAtom);
			addStereos(nativeMol, hStatus, stereos, atomToNativeAtom);
			return buildInchi(hStatus, nativeMol, options);
		} finally {
			IXA.IXA_MOL_Destroy(hStatus, nativeMol);
			IXA.IXA_STATUS_Destroy(hStatus);
		}
	}

	private static Map<InchiAtom, Pointer> addAtoms(Pointer mol, Pointer hStatus, List<InchiAtom> atoms) {
		Map<InchiAtom, Pointer> atomToNativeAtom = new HashMap<>();
		for (InchiAtom atom : atoms) {
			double v;
			int iv;
			String sv;
			// For performance only call IxaAPI when values differ from the defaults
			Pointer nativeAtom = IXA.IXA_MOL_CreateAtom(hStatus, mol);
			atomToNativeAtom.put(atom, nativeAtom);
			if ((v = atom.getX()) != 0) {
				IXA.IXA_MOL_SetAtomX(hStatus, mol, nativeAtom, v);
			}
			if ((v = atom.getY()) != 0) {
				IXA.IXA_MOL_SetAtomY(hStatus, mol, nativeAtom, v);
			}
			if ((v = atom.getZ()) != 0) {
				IXA.IXA_MOL_SetAtomZ(hStatus, mol, nativeAtom, v);
			}
			if (!(sv = atom.getElName()).equals("C")) {
				if (sv.length() > 5) {
					throw new IllegalArgumentException("Element name was too long: " + sv);
				}
				IXA.IXA_MOL_SetAtomElement(hStatus, mol, nativeAtom, sv);
			}
			if ((iv = atom.getIsotopicMass()) != 0) {
				IXA.IXA_MOL_SetAtomMass(hStatus, mol, nativeAtom, iv);
			}
			if ((iv = atom.getCharge()) != 0) {
				IXA.IXA_MOL_SetAtomCharge(hStatus, mol, nativeAtom, iv);
			}
			if ((iv = InchiRadical
					.getCodeObj(atom.getRadical())) != InchiLibrary.IXA_ATOM_RADICAL.IXA_ATOM_RADICAL_NONE) {
				IXA.IXA_MOL_SetAtomRadical(hStatus, mol, nativeAtom, iv);
			}
			if ((iv = atom.getImplicitHydrogen()) != 0) {
				IXA.IXA_MOL_SetAtomHydrogens(hStatus, mol, nativeAtom, 0, iv);
			}
			if ((iv = atom.getImplicitProtium()) != 0) {
				IXA.IXA_MOL_SetAtomHydrogens(hStatus, mol, nativeAtom, 1, iv);
			}
			if ((iv = atom.getImplicitDeuterium()) != 0) {
				IXA.IXA_MOL_SetAtomHydrogens(hStatus, mol, nativeAtom, 2, iv);
			}
			if ((iv = atom.getImplicitTritium()) != 0) {
				IXA.IXA_MOL_SetAtomHydrogens(hStatus, mol, nativeAtom, 3, iv);
			}
		}
		return atomToNativeAtom;
	}

	private static void addBonds(Pointer mol, Pointer hStatus, List<InchiBond> bonds,
			Map<InchiAtom, Pointer> atomToNativeAtom) {
		for (InchiBond bond : bonds) {
			Pointer nativeAtom1 = atomToNativeAtom.get(bond.getStart());
			Pointer nativeAtom2 = atomToNativeAtom.get(bond.getEnd());
			if (nativeAtom1 == null || nativeAtom2 == null) {
				throw new IllegalStateException("Bond referenced an atom that was not provided");
			}
			Pointer nativeBond = IXA.IXA_MOL_CreateBond(hStatus, mol, nativeAtom1, nativeAtom2);
			int iv;
			if ((iv = InchiBondType.getCodeObj(bond.getType())) != InchiLibrary.IXA_BOND_TYPE.IXA_BOND_TYPE_SINGLE) {
				IXA.IXA_MOL_SetBondType(hStatus, mol, nativeBond, iv);
			}
			switch (iv = InchiBondStereo.getCodeObj(bond.getStereo())) {
			case InchiLibrary.tagINCHIBondStereo2D.INCHI_BOND_STEREO_DOUBLE_EITHER:
				// Default is to perceive configuration from 2D coordinates
				IXA.IXA_MOL_SetDblBondConfig(hStatus, mol, nativeBond, IXA_DBLBOND_CONFIG.IXA_DBLBOND_CONFIG_EITHER);
				break;
			case InchiLibrary.tagINCHIBondStereo2D.INCHI_BOND_STEREO_SINGLE_1DOWN:
				IXA.IXA_MOL_SetBondWedge(hStatus, mol, nativeBond, nativeAtom1, IXA_BOND_WEDGE.IXA_BOND_WEDGE_DOWN);
				break;
			case InchiLibrary.tagINCHIBondStereo2D.INCHI_BOND_STEREO_SINGLE_1EITHER:
				IXA.IXA_MOL_SetBondWedge(hStatus, mol, nativeBond, nativeAtom1, IXA_BOND_WEDGE.IXA_BOND_WEDGE_EITHER);
				break;
			case InchiLibrary.tagINCHIBondStereo2D.INCHI_BOND_STEREO_SINGLE_1UP:
				IXA.IXA_MOL_SetBondWedge(hStatus, mol, nativeBond, nativeAtom1, IXA_BOND_WEDGE.IXA_BOND_WEDGE_UP);
				break;
			case InchiLibrary.tagINCHIBondStereo2D.INCHI_BOND_STEREO_SINGLE_2DOWN:
				IXA.IXA_MOL_SetBondWedge(hStatus, mol, nativeBond, nativeAtom2, IXA_BOND_WEDGE.IXA_BOND_WEDGE_DOWN);
				break;
			case InchiLibrary.tagINCHIBondStereo2D.INCHI_BOND_STEREO_SINGLE_2EITHER:
				IXA.IXA_MOL_SetBondWedge(hStatus, mol, nativeBond, nativeAtom2, IXA_BOND_WEDGE.IXA_BOND_WEDGE_EITHER);
				break;
			case InchiLibrary.tagINCHIBondStereo2D.INCHI_BOND_STEREO_SINGLE_2UP:
				IXA.IXA_MOL_SetBondWedge(hStatus, mol, nativeBond, nativeAtom2, IXA_BOND_WEDGE.IXA_BOND_WEDGE_UP);
				break;
			default:
			case InchiLibrary.tagINCHIBondStereo2D.INCHI_BOND_STEREO_NONE:
				break;
			}
		}
	}

	private static void addStereos(Pointer nativeMol, Pointer hStatus, List<InchiStereo> stereos,
			Map<InchiAtom, Pointer> atomToNativeAtom) {
		for (InchiStereo stereo : stereos) {
			int type = InchiStereoType.getCodeObj(stereo.getType());
			if (type == InchiLibrary.tagINCHIStereoType0D.INCHI_StereoType_None) {
				continue;
			}
			InchiAtom[] neighbors = stereo.getAtoms();
			Pointer vertex1 = getStereoVertex(atomToNativeAtom, neighbors[0]);
			Pointer vertex2 = getStereoVertex(atomToNativeAtom, neighbors[1]);
			Pointer vertex3 = getStereoVertex(atomToNativeAtom, neighbors[2]);
			Pointer vertex4 = getStereoVertex(atomToNativeAtom, neighbors[3]);

			Pointer center;
			Pointer centralAtom;
			switch (type) {
			case InchiLibrary.tagINCHIStereoType0D.INCHI_StereoType_Tetrahedral: {
				centralAtom = getStereoCentralAtom(stereo, atomToNativeAtom);
				center = IXA.IXA_MOL_CreateStereoTetrahedron(hStatus, nativeMol, centralAtom, vertex1, vertex2, vertex3,
						vertex4);
				break;
			}
			case InchiLibrary.tagINCHIStereoType0D.INCHI_StereoType_Allene: {
				centralAtom = getStereoCentralAtom(stereo, atomToNativeAtom);
				center = IXA.IXA_MOL_CreateStereoAntiRectangle(hStatus, nativeMol, centralAtom, vertex1, vertex2,
						vertex3, vertex4);
				break;
			}
			case InchiLibrary.tagINCHIStereoType0D.INCHI_StereoType_DoubleBond: {
				Pointer centralBond = IXA.IXA_MOL_GetCommonBond(hStatus, nativeMol, vertex2, vertex3);
				if (centralBond == null) {
					throw new IllegalStateException("Could not find olefin/cumulene central bond");
				}
				// We intentionally pass dummy values for vertex2/vertex3, as the IXA API
				// doesn't actually need these as long as vertex1 and vertex4 aren't implicit
				// hydrogen
				// this will be -1 in the case of JavaScript WASM
				center = IXA.IXA_MOL_CreateStereoRectangle(hStatus, nativeMol, centralBond, vertex1, IXA.ATOM_IMPLICIT_H,
						IXA.ATOM_IMPLICIT_H, vertex4);
				break;
			}
			default:
				throw new IllegalStateException("Unexpected InChI stereo type:" + type);
			}
			int parity = InchiStereoParity.getCodeObj(stereo.getParity());
			IXA.IXA_MOL_SetStereoParity(hStatus, nativeMol, center, parity);
		}
	}

	private static Pointer getStereoCentralAtom(InchiStereo stereo, Map<InchiAtom, Pointer> atomToNativeAtom)
			throws IllegalStateException {
		InchiAtom centralAtom = stereo.getCentralAtom();
		Pointer nativeCentral = atomToNativeAtom.get(centralAtom);
		if (nativeCentral == null) {
			throw new IllegalStateException("Stereo configuration central atom referenced an atom that does not exist");
		}
		return nativeCentral;
	}

	private static Pointer getStereoVertex(Map<InchiAtom, Pointer> atomToNativeAtom, InchiAtom inchiAtom) {
		if (InchiStereo.STEREO_IMPLICIT_H == inchiAtom) {
			return IXA.ATOM_IMPLICIT_H;
		}
		Pointer vertex = atomToNativeAtom.get(inchiAtom);
		if (vertex == null) {
			throw new IllegalStateException("Stereo configuration referenced an atom that does not exist");
		}
		return vertex;
	}

	private static InchiOutput buildInchi(Pointer hStatus, Pointer nativeMol, InchiOptions options) {
		Pointer builder = IXA.IXA_INCHIBUILDER_Create(hStatus);
		try {
			IXA.IXA_INCHIBUILDER_SetMolecule(hStatus, builder, nativeMol);

			long timeoutMilliSecs = options.getTimeoutMilliSeconds();
			if (timeoutMilliSecs != 0) {
				IXA.IXA_INCHIBUILDER_SetOption_Timeout_MilliSeconds(hStatus, builder, timeoutMilliSecs);
			}
			for (InchiFlag flag : options.getFlags()) {
				switch (flag) {
				case AuxNone:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							InchiLibrary.IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_AuxNone, true);
					break;
				case ChiralFlagOFF:
					IXA.IXA_MOL_SetChiral(hStatus, nativeMol, false);
					break;
				case ChiralFlagON:
					IXA.IXA_MOL_SetChiral(hStatus, nativeMol, true);
					break;
				case DoNotAddH:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_DoNotAddH, true);
					break;
				case FixedH:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_FixedH, true);
					break;
				case KET:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder, IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_KET,
							true);
					break;
				case LargeMolecules:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_LargeMolecules, true);
					break;
				case NEWPSOFF:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_NewPsOff, true);
					break;
				case OneFiveT:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder, IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_15T,
							true);
					break;
				case RecMet:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_RecMet, true);
					break;
				case SLUUD:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_SLUUD, true);
					break;
				case SNon:
					IXA.IXA_INCHIBUILDER_SetOption_Stereo(hStatus, builder,
							IXA_INCHIBUILDER_STEREOOPTION.IXA_INCHIBUILDER_STEREOOPTION_SNon);
					break;
				case SRac:
					IXA.IXA_INCHIBUILDER_SetOption_Stereo(hStatus, builder,
							IXA_INCHIBUILDER_STEREOOPTION.IXA_INCHIBUILDER_STEREOOPTION_SRac);
					break;
				case SRel:
					IXA.IXA_INCHIBUILDER_SetOption_Stereo(hStatus, builder,
							IXA_INCHIBUILDER_STEREOOPTION.IXA_INCHIBUILDER_STEREOOPTION_SRel);
					break;
				case SUCF:
					IXA.IXA_INCHIBUILDER_SetOption_Stereo(hStatus, builder,
							IXA_INCHIBUILDER_STEREOOPTION.IXA_INCHIBUILDER_STEREOOPTION_SUCF);
					break;
				case SAbs:
					IXA.IXA_INCHIBUILDER_SetOption_Stereo(hStatus, builder,
							IXA_INCHIBUILDER_STEREOOPTION.IXA_INCHIBUILDER_STEREOOPTION_SAbs);
					break;
				case SUU:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder, IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_SUU,
							true);
					break;
				case SaveOpt:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_SaveOpt, true);
					break;
				case WarnOnEmptyStructure:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_WarnOnEmptyStructure, true);
					break;
				case NoWarnings:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_NoWarnings, true);
					break;
				case LooseTSACheck:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_LooseTSACheck, true);
					break;
				case Polymers:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_Polymers, true);
					break;
				case Polymers105:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_Polymers105, true);
					break;
				case FoldCRU:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_FoldCRU, true);
					break;
				case NoFrameShift:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_NoFrameShift, true);
					break;
				case NoEdits:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_NoEdits, true);
					break;
				case NPZz:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_NPZZ, true);
					break;
				case SAtZz:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_SATZZ, true);
					break;
				case OutErrInChI:
					IXA.IXA_INCHIBUILDER_SetOption(hStatus, builder,
							IXA_INCHIBUILDER_OPTION.IXA_INCHIBUILDER_OPTION_OutErrInChI, true);
					break;
				default:
					throw new IllegalStateException("Unexpected InChI option flag: " + flag);
				}
			}

			String inchi = IXA.IXA_INCHIBUILDER_GetInChI(hStatus, builder);
			String auxInfo = IXA.IXA_INCHIBUILDER_GetAuxInfo(hStatus, builder);
			String log = IXA.IXA_INCHIBUILDER_GetLog(hStatus, builder);
			InchiStatus status = getStatus(hStatus);
			String messages = getMessages(hStatus);
			return new InchiOutput(inchi, auxInfo, messages, log, status);
		} finally {
			IXA.IXA_INCHIBUILDER_Destroy(hStatus, builder);
		}
	}

	private static InchiStatus getStatus(Pointer hStatus) {
		return (IXA.IXA_STATUS_HasError(hStatus) ? InchiStatus.ERROR
				: IXA.IXA_STATUS_HasWarning(hStatus) ? InchiStatus.WARNING : InchiStatus.SUCCESS);
	}

	private static String getMessages(Pointer hStatus) {
		StringBuilder sb = new StringBuilder();
		int messageCount = IXA.IXA_STATUS_GetCount(hStatus);
		for (int i = 0; i < messageCount; i++) {
			if (i > 0) {
				sb.append("; ");
			}
			sb.append(IXA.IXA_STATUS_GetMessage(hStatus, i));
		}
		return sb.toString();
	}

	public static InchiOutput molToInchi(String molText) {
		return molFileToInchi(molText, InchiOptions.DEFAULT_OPTIONS);
	}

	/**
	 * Generate an InChI from a MOL file as input.
	 * 
	 * For compatibility with JnaInchi. Deprecated only because of its ambiguous
	 * name; Replacement is molFileToInch(String molText).
	 * 
	 * @param molText molfile text
	 * @param options
	 * @return
	 */
	@Deprecated
	public static InchiOutput molToInchi(String molText, InchiOptions options) {
		return molFileToInchi(molText, options);
	}

	/**
	 * Generate an InChI from a MOL file as input
	 * 
	 * @param molText
	 * @param options
	 * @return
	 */
	public static InchiOutput molFileToInchi(String molText, InchiOptions options) {
		checkLibrary();
		Pointer hStatus = IXA.IXA_STATUS_Create();
		Pointer hMolecule = IXA.IXA_MOL_Create(hStatus);		
		IXA.IXA_MOL_ReadMolfile(hStatus, hMolecule, molText);
		if (IXA.IXA_STATUS_HasError(hStatus)) {
			return new InchiOutput("", "", getMessages(hStatus), "", InchiStatus.ERROR);
		}
		return buildInchi(hStatus, hMolecule, options);
	}

	/**
	 * Converts InChI into InChI for validation purposes. It may also be used to
	 * filter out specific layers. For instance, SNon would remove the
	 * stereochemical layer. Omitting FixedH and/or RecMet would remove Fixed-H or
	 * Reconnected layers.
	 * 
	 * @param inchi
	 * @param options
	 * @return
	 */
	public static InchiOutput inchiToInchi(String inchi, InchiOptions options) {
		checkLibrary();
		Pointer hStatus = IXA.IXA_STATUS_Create();
		Pointer nativeMol = IXA.IXA_MOL_Create(hStatus);
		try {
			IXA.IXA_MOL_ReadInChI(hStatus, nativeMol, inchi);
			return buildInchi(hStatus, nativeMol, options);
		} finally {
			IXA.IXA_MOL_Destroy(hStatus, nativeMol);
		}
	}

	/**
	 * Generate an InChIKey from an InChI, with no option to return hash codes.
	 * 
	 * @param inchi
	 * @return
	 */
	public static InchiKeyOutput inchiToInchiKeyIXA(String inchi) {
		checkLibrary();
		Pointer hStatus = IXA.IXA_STATUS_Create();
		Pointer hBuilder = IXA.IXA_INCHIKEYBUILDER_Create(hStatus);
		try {
			IXA.IXA_INCHIKEYBUILDER_SetInChI(hStatus, hBuilder, inchi);
			String key = IXA.IXA_INCHIKEYBUILDER_GetInChIKey(hStatus, hBuilder);
			int ret = (key == null ? InchiLibrary.INCHIKEY_UNKNOWN_ERROR 
					: InchiLibrary.INCHIKEY_OK);
			return new InchiKeyOutput(key, InchiKeyStatus.of((byte)ret), "", "");
		} finally {
			IXA.IXA_STATUS_Destroy(hStatus);
			IXA.IXA_INCHIBUILDER_Destroy(null, hBuilder);
		}
	}

	/**
	 * Generate an 27-byte InChIKey from an InChI along with 64-byte hash codes and detailed error message.
	 * 
	 * JavaScript-safe even though not IXA
	 * 
	 * @param inchi
	 * @return InchiKeyOutput object
	 */
	public static InchiKeyOutput inchiToInchiKey(String inchi) {
	    checkLibrary();
	    byte[] inchiKeyBytes = new byte[28];
	    byte[] szXtra1Bytes = new byte[65];
	    byte[] szXtra2Bytes = new byte[65];
	    InchiKeyStatus ret = InchiKeyStatus.of(InchiLibrary.GetINCHIKeyFromINCHI(inchi, 1, 1, inchiKeyBytes, szXtra1Bytes, szXtra2Bytes));
		try {
		    String inchiKeyStr = new String(inchiKeyBytes, "UTF-8").trim();
		    String szXtra1 = new String(szXtra1Bytes, "UTF-8").trim();
		    String szXtra2 = new String(szXtra2Bytes, "UTF-8").trim();
		    return new InchiKeyOutput(inchiKeyStr, ret, szXtra1, szXtra2);
		} catch (UnsupportedEncodingException e) {
			// n/a just using UTF-8 here because it avoids unnecessary class loading in JavaScript
			return null;
		}
	}

	/**
	 * Check if the string represents a valid InChI/StdInChI If strict is true, try
	 * to perform InChI2InChI conversion; returns success if a resulting InChI
	 * string exactly matches source. Be cautious: the result may be too strict,
	 * i.e. a 'false alarm', due to imperfection of conversion.
	 * 
	 * JavaScript-safe even though not IXA
	 * 
	 * @param inchi
	 * @param strict if false, just briefly check for proper layout (prefix,
	 *               version, etc.)
	 * @return InchiCheckStatus
	 */
	  public static InchiCheckStatus checkInchi(String inchi, boolean strict) {
	    checkLibrary();
	    return InchiCheckStatus.of(InchiLibrary.CheckINCHI(inchi, strict));
	  }
	  
	/**
	 * Check if the string represents valid InChIKey
	 * 
	 * JavaScript-safe even though not IXA
	 * 
	 * @param inchiKey
	 * @return InchiKeyCheckStatus
	 */
	  public static InchiKeyCheckStatus checkInchiKey(String inchiKey) {
	    checkLibrary();
	    return InchiKeyCheckStatus.of(InchiLibrary.CheckINCHIKey(inchiKey));
	  }
	  

	/**
	 * Creates the input data structure for InChI generation out of the auxiliary
	 * information (AuxInfo) string produced by previous InChI generator calls
	 * 
	 * @param auxInfo           contains ASCIIZ string of InChI output for a single
	 *                          structure or only the AuxInfo line
	 * @param doNotAddH         if true then InChI will not be allowed to add
	 *                          implicit H
	 * @param diffUnkUndfStereo if true, use different labels for unknown and
	 *                          undefined stereo
	 * @return
	 */
	public static InchiInputFromAuxinfoOutput getInchiInputFromAuxInfo(String auxInfo, boolean doNotAddH,
			boolean diffUnkUndfStereo) {
	    checkLibrary();
		// not implemented in CDK
		// TODO
		// there is currently no IXA access to ichilnct.c
		// Get_inchi_Input_FromAuxInfo( szInchiAuxInfo, bDoNotAddH, bDiffUnkUndfStereo,
		// pInchiInp )
		InchiInput inchiInput = new InchiInput();
		InchiStatus status = InchiStatus.ERROR;
		String message = "No IXA interface for INCHI from auxinfo";
		Boolean chiralFlag = null;
		return new InchiInputFromAuxinfoOutput(inchiInput, chiralFlag, message, status);
	}

//	// TODO InchiLibrary.Get_inchi_Input_FromAuxInfo
//  private static InchiStatus getInchiStatus(int ret) {
//    switch (ret) {
//    case tagRetValGetINCHI.inchi_Ret_OKAY:/* Success; no errors or warnings*/
//      return InchiStatus.SUCCESS;
//    case tagRetValGetINCHI.inchi_Ret_EOF:/* no structural data has been provided */
//    case tagRetValGetINCHI.inchi_Ret_WARNING:/* Success; warning(s) issued*/
//      return InchiStatus.WARNING;
//    case tagRetValGetINCHI.inchi_Ret_ERROR:/* Error: no InChI has been created */
//    case tagRetValGetINCHI.inchi_Ret_FATAL:/* Severe error: no InChI has been created (typically, memory allocation failure) */
//    case tagRetValGetINCHI.inchi_Ret_UNKNOWN:/* Unknown program error */
//    case tagRetValGetINCHI.inchi_Ret_BUSY:/* Previous call to InChI has not returned yet*/
//      return InchiStatus.ERROR;
//    default:
//      return InchiStatus.ERROR;
//    }
//  }


	public static InchiInputFromInchiOutput getInchiInputFromInchi(String inchi) {
		return getInchiInputFromInchi(inchi, InchiOptions.DEFAULT_OPTIONS);
	}

	public static InchiInputFromInchiOutput getInchiInputFromInchi(String inchi, InchiOptions options) {
		checkLibrary();
		Pointer hStatus = IXA.IXA_STATUS_Create();
		Pointer hMolecule = IXA.IXA_MOL_Create(hStatus);

		try {
			// does not allow for options
			// jnainchi calls getInchiStatus(InchiLibrary.GetStructFromINCHI(input,
			// output));
			// but here we call IXA_MOL_ReadInChI, which just sets options to ""
			IXA.IXA_MOL_ReadInChI(hStatus, hMolecule, inchi);
			InchiInput inchiInput = new InchiInput();
			Map<Pointer, InchiAtom> mapNativeToJavaAtom = new HashMap<>();
			nativeToJavaAtoms(hStatus, hMolecule, mapNativeToJavaAtom, inchiInput);
			nativeToJavaBonds(hStatus, hMolecule, mapNativeToJavaAtom, inchiInput);
			nativeToJavaStereos(hStatus, hMolecule, mapNativeToJavaAtom, inchiInput);

			String message = getMessages(hStatus);
			// getStructureFromINCHI generates an output
			// but IXA_MOL_ReadInChI ignores output.WarningFlags.
			// and output.szLog. Perhaps these could be rolled into the message,
			// but probably we could do without them.
//            String log = output.szLog;
//		      NativeLong[] nativeFlags = output.WarningFlags;//This is a flattened multi-dimensional array, unflatten as we convert
			// convert
			String log = "";
			long[][] warningFlags = new long[2][2];
//			for (int i = 0; i < nativeFlags.length; i++) {
//				long val = nativeFlags[i].longValue();
//				switch (i) {
//				case 0:
//					warningFlags[0][0] = val;
//					break;
//				case 1:
//					warningFlags[0][1] = val;
//					break;
//				case 2:
//					warningFlags[1][0] = val;
//					break;
//				case 3:
//					warningFlags[1][1] = val;
//					break;
//				default:
//					break;
//				}
//			}
			return new InchiInputFromInchiOutput(inchiInput, message, log, getStatus(hStatus), warningFlags);
		} catch(Throwable t) {
			System.err.println(t);
			return null;
		} finally {
			IXA.IXA_STATUS_Destroy(hStatus);
			IXA.IXA_MOL_Destroy(null, hMolecule);
		}
	}

	private static void nativeToJavaAtoms(Pointer hStatus, Pointer hMolecule,
			Map<Pointer, InchiAtom> mapNativeToJavaAtom, InchiInput inchiInput) {
		int nAtoms = IXA.IXA_MOL_GetNumAtoms(hStatus, hMolecule);
		for (int i = 0; i < nAtoms; i++) {
			Pointer hAtom = IXA.IXA_MOL_GetAtomId(hStatus, hMolecule, i);
			String elSymbol = IXA.IXA_MOL_GetAtomElement(hStatus, hMolecule, hAtom);
			double x = IXA.IXA_MOL_GetAtomX(hStatus, hMolecule, hAtom);
			double y = IXA.IXA_MOL_GetAtomY(hStatus, hMolecule, hAtom);
			double z = IXA.IXA_MOL_GetAtomZ(hStatus, hMolecule, hAtom);
			InchiAtom atom = new InchiAtom(elSymbol, x, y, z);
			atom.setImplicitHydrogen(IXA.IXA_MOL_GetAtomHydrogens(hStatus, hMolecule, hAtom, 0));
			atom.setImplicitProtium(IXA.IXA_MOL_GetAtomHydrogens(hStatus, hMolecule, hAtom, 1));
			atom.setImplicitDeuterium(IXA.IXA_MOL_GetAtomHydrogens(hStatus, hMolecule, hAtom, 2));
			atom.setImplicitTritium(IXA.IXA_MOL_GetAtomHydrogens(hStatus, hMolecule, hAtom, 3));
			int isotopicMass = IXA.IXA_MOL_GetAtomMass(hStatus, hMolecule, hAtom);
			if (isotopicMass >= ISOTOPIC_SHIFT_RANGE_MIN && isotopicMass <= ISOTOPIC_SHIFT_RANGE_MAX) {
				// isotopic mass contains a delta from a hardcoded base mass
				int baseMass = aveMass.getOrDefault(elSymbol, 0);
				int delta = isotopicMass - InchiLibrary.ISOTOPIC_SHIFT_FLAG;
				isotopicMass = baseMass + delta;
			}
			atom.setIsotopicMass(isotopicMass);
			atom.setRadical(InchiRadical.of((byte)IXA.IXA_MOL_GetAtomRadical(hStatus, hMolecule, hAtom)));
			atom.setCharge(IXA.IXA_MOL_GetAtomCharge(hStatus, hMolecule, hAtom));
			inchiInput.addAtom(atom);
			mapNativeToJavaAtom.put((hAtom), atom);
		}
	}

	private static void nativeToJavaBonds(Pointer hStatus, Pointer hMolecule,
			Map<Pointer, InchiAtom> mapNativeToJavaAtom, InchiInput inchiInput) {
		int numBonds = IXA.IXA_MOL_GetNumBonds(hStatus, hMolecule);
		for (int i = 0; i < numBonds; i++) {
			Pointer hBond = IXA.IXA_MOL_GetBondId(hStatus, hMolecule, i);
			InchiBondType bondType = InchiBondType.of((byte)IXA.IXA_MOL_GetBondType(hStatus, hMolecule, hBond));
			Pointer a1 = IXA.IXA_MOL_GetBondAtom1(hStatus, hMolecule, hBond);
			Pointer a2 = IXA.IXA_MOL_GetBondAtom2(hStatus, hMolecule, hBond);
			// maybe?
			InchiBondStereo bondStereo = InchiBondStereo.of((byte)IXA.IXA_MOL_GetBondWedge(hStatus, hMolecule, hBond, a1));
			InchiAtom atom1 = mapNativeToJavaAtom.get(a1);
			InchiAtom atom2 = mapNativeToJavaAtom.get(a2);
			inchiInput.addBond(new InchiBond(atom1, atom2, bondType, bondStereo));
		}
	}

	private static void nativeToJavaStereos(Pointer hStatus, Pointer hMolecule,
			Map<Pointer, InchiAtom> mapNativeToJavaAtom, InchiInput inchiInput) {
		int numStereo = IXA.IXA_MOL_GetNumStereos(hStatus, hMolecule);
		for (int is = 0; is < numStereo; is++) {
			InchiAtom[] atoms = new InchiAtom[4];
			Pointer hStereo = IXA.IXA_MOL_GetStereoId(hStatus, hMolecule, is);
			// idxToAtom will give null for -1 input (implicit hydrogen)
			for (int i = 0; i < 4; i++) {
				Pointer vertex = IXA.IXA_MOL_GetStereoVertex(hStatus, hMolecule, hStereo, i);
				atoms[i] = mapNativeToJavaAtom.get(vertex);
				if (atoms[i] == null)
					atoms[i] = InchiStereo.STEREO_IMPLICIT_H;
			}
			checkStatus(hStatus);
			InchiStereoType stereoType;
			// note that these are NOT the same order as StereoType
			int topo = IXA.IXA_MOL_GetStereoTopology(hStatus, hMolecule, hStereo);
			boolean hasCentralAtom = false;
			switch (topo) {
			case InchiLibrary.IXA_STEREO_TOPOLOGY.IXA_STEREO_TOPOLOGY_TETRAHEDRON:
				stereoType = InchiStereoType.Tetrahedral;
				hasCentralAtom = true;
				break;
			case InchiLibrary.IXA_STEREO_TOPOLOGY.IXA_STEREO_TOPOLOGY_RECTANGLE:
				stereoType = InchiStereoType.DoubleBond;
				// or 2,3,4-cumulene, I think.
				Pointer centralBond = IXA.IXA_MOL_GetStereoCentralBond(hStatus, hMolecule, hStereo);
				Pointer internal1 = IXA.IXA_MOL_GetBondAtom1(hStatus, hMolecule, centralBond);
				Pointer internal2 = IXA.IXA_MOL_GetBondAtom2(hStatus, hMolecule, centralBond);
				atoms[1] = mapNativeToJavaAtom.get(internal1);
				atoms[2] = mapNativeToJavaAtom.get(internal2);
				break;
			case InchiLibrary.IXA_STEREO_TOPOLOGY.IXA_STEREO_TOPOLOGY_ANTIRECTANGLE:
				stereoType = InchiStereoType.Allene;
				hasCentralAtom = true;
				break;
			default:
				return;
			}
			InchiStereoParity parity = InchiStereoParity.of((byte)IXA.IXA_MOL_GetStereoParity(hStatus, hMolecule, hStereo));
			InchiAtom centralAtom = (hasCentralAtom ?  centralAtom = mapNativeToJavaAtom.get(IXA.IXA_MOL_GetStereoCentralAtom(hStatus, hMolecule, hStereo)) : null);
			inchiInput.addStereo(new InchiStereo(atoms, centralAtom, stereoType, parity));
		}
	}

	private static void checkStatus(Pointer hStatus) {
		int n = (hStatus == null ? 0 : IXA.IXA_STATUS_GetCount(hStatus));
		if (n > 0) {
			System.out.println(n);
			System.out.println(IXA.IXA_STATUS_GetMessage(hStatus, n - 1));
		}
	}

	/**
	 * Returns the version of the wrapped InChI C library
	 * 
	 * @return Version number String
	 */
	public static String getInchiLibraryVersion() {
		/**
		 * needs implementation
		 * 
		 * @j2sNative
		 * 
		 * return null;
		 * 
		 */
		try (InputStream is = JnaInchi.class.getResourceAsStream("jnainchi_build.props")) {
			Properties props = new Properties();
			props.load(is);
			return props.getProperty("inchi_version");
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Returns the version of the JNA-InChI Java library
	 * 
	 * @return Version number String
	 */
	public static String getJnaInchiVersion() {
		/**
		 * not implemented;
		 * 
		 * @j2sNative
		 */
		try (InputStream is = JnaInchi.class.getResourceAsStream("jnainchi_build.props")) {
			Properties props = new Properties();
			props.load(is);
			return props.getProperty("jnainchi_version");
		} catch (Exception e) {
			return null;
		}
	}

}
