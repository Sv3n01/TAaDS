package main;

public class Skill {
    public String conceptUri;
    public String preferredLabel;
    public String altLabels;

    public Skill(String conceptUri, String preferredLabel, String altLabels){
        this.conceptUri = conceptUri;
        this.preferredLabel = preferredLabel;
        this.altLabels = altLabels;
    }
}
