import com.twistedmatrix.amp.AmpItem;

/** This class is used to contain the variables in an AmpList.
 * All variables must be public and a parameterless constructor must exist. */

public class CountItem extends AmpItem {
    public int a = 0;
    public String b = "";
    
    public CountItem() { } // Required
    public CountItem(int aval, String bval) { a = aval; b = bval; }
    
    public String toString() { return a + " -> " + b; }
}

