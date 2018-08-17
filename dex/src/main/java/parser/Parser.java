package parser;

import code.IParser;


public class Parser implements IParser {

    private static final int VERSION = 1; // TODO


    @Override
    public int getVersion() {
        return VERSION;
    }


    @Override
    public boolean testIsOk() {
        return true;
    }
}
