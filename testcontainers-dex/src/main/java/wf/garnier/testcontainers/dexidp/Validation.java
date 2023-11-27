package wf.garnier.testcontainers.dexidp;

class Validation {
    public static void assertNotBlank(String property, String propertyName) {
        if (property == null) {
            throw new IllegalArgumentException(propertyName + "must not be null");
        }
        if (property.isBlank()) {
            throw new IllegalArgumentException(propertyName + "must not be blank");
        }
    }
}
