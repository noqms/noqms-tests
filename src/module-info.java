module com.noqms.tests {
    requires com.noqms;
    requires gson;
    requires java.sql; // for gson until https://github.com/google/gson/pull/1500 kicks in
    
    opens com.noqms.tests.load to gson;
    opens com.noqms.tests.interaction to gson;
    opens com.noqms.tests.roundtrip to gson;
    opens com.noqms.tests.tweedle to gson;
    
    exports com.noqms.tests.load to com.noqms;
    exports com.noqms.tests.interaction to com.noqms;
    exports com.noqms.tests.roundtrip to com.noqms;
    exports com.noqms.tests.tweedle to com.noqms;
}
