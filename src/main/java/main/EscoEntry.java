package main;

public class EscoEntry {
    public String conceptUri;
    public String preferredLabel;
    public String altLabels;
    public String type;//Skill,Occupation,Concept


    public EscoEntry(String conceptUri, String preferredLabel, String altLabels,String type){
        this.conceptUri = conceptUri;
        this.preferredLabel = preferredLabel;
        this.altLabels = altLabels;
        this.type = type;
    }
}
