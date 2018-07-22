package inchi;
import java.util.Arrays;
import java.util.List;

import com.sun.jna.NativeLong;
import com.sun.jna.Structure;
import com.sun.jna.Structure.ByReference;
/**
 * This file was autogenerated by <a href="http://jnaerator.googlecode.com/">JNAerator</a>,<br>
 * a tool written by <a href="http://ochafik.com/">Olivier Chafik</a> that <a href="http://code.google.com/p/jnaerator/wiki/CreditsAndLicense">uses a few opensource projects.</a>.<br>
 * For help, please visit <a href="http://nativelibs4java.googlecode.com/">NativeLibs4Java</a> , <a href="http://rococoa.dev.java.net/">Rococoa</a>, or <a href="http://jna.dev.java.net/">JNA</a>.
 */
public class tagNormAtomData extends Structure implements ByReference {
  /**
   * atom list<br>
   * C type : NORM_ATOM*
   */
  public inchi.tagNormAtom at;
  /**
   * atom list with added or removed protons only<br>
   * C type : NORM_ATOM*
   */
  public inchi.tagNormAtom at_fixed_bonds;
  /** number of atoms except removed terminal H */
  public int num_at;
  /** number of removed H; at[] has (num_at+num_removed_H) elements */
  public int num_removed_H;
  public int num_bonds;
  /** number of isotopic atoms */
  public int num_isotopic;
  /** for internal use */
  public int bExists;
  /** for internal use */
  public int bDeleted;
  public int bHasIsotopicLayer;
  public int bTautomeric;
  /** for internal use */
  public int bTautPreprocessed;
  public int nNumRemovedProtons;
  /** C type : NUM_HS[3] */
  public short[] nNumRemovedProtonsIsotopic = new short[3];
  /** C type : NUM_HS[3] */
  public short[] num_iso_H = new short[3];
  /**
   * for internal use<br>
   * C type : INCHI_MODES
   */
  public NativeLong bTautFlags;
  /**
   * for internal use<br>
   * C type : INCHI_MODES
   */
  public NativeLong bTautFlagsDone;
  /**
   * for internal use<br>
   * C type : INCHI_MODES
   */
  public NativeLong bNormalizationFlags;

  protected List<String> getFieldOrder() {
    return Arrays.asList("at", "at_fixed_bonds", "num_at", "num_removed_H", "num_bonds", "num_isotopic", "bExists", "bDeleted", "bHasIsotopicLayer", "bTautomeric", "bTautPreprocessed", "nNumRemovedProtons", "nNumRemovedProtonsIsotopic", "num_iso_H", "bTautFlags", "bTautFlagsDone", "bNormalizationFlags");
  }
}
