package com.elibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "library")
public class LibraryProperties {
    private SuperAdmin superAdmin = new SuperAdmin();
    private Pdf pdf = new Pdf();

    public SuperAdmin getSuperAdmin() { return superAdmin; }
    public void setSuperAdmin(SuperAdmin superAdmin) { this.superAdmin = superAdmin; }
    public Pdf getPdf() { return pdf; }
    public void setPdf(Pdf pdf) { this.pdf = pdf; }

    public static class SuperAdmin {
        private String username;
        private String password;
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class Pdf {
        private String storageDir = "./uploads/pdf";
        private int maxSizeMb = 50;
        public String getStorageDir() { return storageDir; }
        public void setStorageDir(String storageDir) { this.storageDir = storageDir; }
        public int getMaxSizeMb() { return maxSizeMb; }
        public void setMaxSizeMb(int maxSizeMb) { this.maxSizeMb = maxSizeMb; }
    }
}
