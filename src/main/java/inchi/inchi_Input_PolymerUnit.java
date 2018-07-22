package inchi;
import java.util.Arrays;
import java.util.List;

import com.sun.jna.Structure;
import com.sun.jna.Structure.ByReference;
import com.sun.jna.ptr.IntByReference;
/**
 * This file was autogenerated by <a href="http://jnaerator.googlecode.com/">JNAerator</a>,<br>
 * a tool written by <a href="http://ochafik.com/">Olivier Chafik</a> that <a href="http://code.google.com/p/jnaerator/wiki/CreditsAndLicense">uses a few opensource projects.</a>.<br>
 * For help, please visit <a href="http://nativelibs4java.googlecode.com/">NativeLibs4Java</a> , <a href="http://rococoa.dev.java.net/">Rococoa</a>, or <a href="http://jna.dev.java.net/">JNA</a>.
 */
public class inchi_Input_PolymerUnit extends Structure implements ByReference {
  /** Unit id; it is what is called 'Sgroup number' */
  public int id;
  /** Unit type as per CTFile format (STY) */
  public int type;
  /** Unit subtype as per CTFile format (SST) */
  public int subtype;
  /** Unit connection scheme  as per CTFile format (SCN) */
  public int conn;
  /** One more unit id; what is called 'unique Sgroup */
  public int label;
  /** Number of atoms in the unit */
  public int na;
  /** Number of bonds in the unit */
  public int nb;
  /**
   * Bracket ends coordinates (SDI)<br>
   * C type : double[4]
   */
  public double[] xbr1 = new double[4];
  /**
   * Bracket ends coordinates (SDI)<br>
   * C type : double[4]
   */
  public double[] xbr2 = new double[4];
  /**
   * Sgroup Subscript (SMT) ('n' or so )<br>
   * C type : char[80]
   */
  public byte[] smt = new byte[80];
  /**
   * List of atoms in the unit (SAL), atomic numbers<br>
   * C type : int*
   */
  public IntByReference alist;
  /**
   * List of crossing bonds of unit:<br>
   * C type : int*
   */
  public IntByReference blist;

  protected List<String> getFieldOrder() {
    return Arrays.asList("id", "type", "subtype", "conn", "label", "na", "nb", "xbr1", "xbr2", "smt", "alist", "blist");
  }
}
