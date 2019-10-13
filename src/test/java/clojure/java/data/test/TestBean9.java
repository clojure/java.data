package clojure.java.data.test;
import java.util.Map;

public class TestBean9 {

    private boolean aBool;
    private Boolean aBoolean;
    private String aString;

    public void setABool(boolean b) {
        this.aBool = b;
    }

    public boolean isABool() {
        return aBool;
    }

    public void setABoolean(Boolean b) {
        this.aBoolean = b;
    }

    // not a getter because 'is' only works for primitive boolean
    public Boolean isABoolean() {
        return aBoolean;
    }

    public void setAString(String s) {
        this.aString = s;
    }

    public String getAString() {
        return aString;
    }
}
