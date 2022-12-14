package main;

import com.opencsv.CSVReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.lucene.analysis.en.EnglishAnalyzer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) {
        /*
        get the matching esco skills for a chosen module
         */
        List<Skill> skills = getSkillsForModule(0);
        //print first 10 skills
        for(int i=0;i<10;i++){
            System.out.println(skills.get(i).preferredLabel);
        }
    }

    //returns hashmap key: full words string, value: frequency of the word stem
    public static LinkedHashMap getMostImportantTerms(int moduleIndex){
        LinkedHashMap<String, Double> rankFullWords = new LinkedHashMap<>();
        try {
            //PDF to text
            PDDocument doc = PDDocument.load(new File("src/main/java/main/modules.pdf"));
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(doc);
            Files.write(Paths.get("src/main/java/main/result.txt"), text.getBytes());

            //PART 2

            //get chapters
            String[] chapters1 = text.split("Module Name|Modul Name");
            //remove the first chapter (table of contents)
            String[] chapters = new String[chapters1.length-1];
            for(int i=1; i<chapters1.length;i++){
                chapters[i-1] = chapters1[i];
            }

            //Tokenize / Stemming
            EnglishAnalyzer analyzer = new EnglishAnalyzer();
            TokenStream tokenStream;
            String[] chaptersTokenized = new String[chapters.length];
            for(int i=0;i<chapters.length;i++){
                tokenStream
                        = analyzer.tokenStream("contents",
                        new StringReader(chapters[i]));

                CharTermAttribute attribute
                        = tokenStream.addAttribute(CharTermAttribute.class);
                String result = "";
                tokenStream.reset();
                while (tokenStream.incrementToken()) {
                    if(attribute.toString().length() > 3){//min. 4 letters
                        result += attribute.toString()+" ";
                    }
                }
                chaptersTokenized[i] = result;
                tokenStream.close();
            }

            //Ranking
            List<HashMap<String, Double>> rankPerChapter = new ArrayList<HashMap<String, Double>>();
            for(int i=0;i<chaptersTokenized.length;i++){
                rankPerChapter.add(new HashMap<String,Double>());
                HashMap<String, Double> rank = rankPerChapter.get(i);

                String chapter = chaptersTokenized[i];
                String[] terms = chapter.split(" ");
                for(String term : terms){
                    if(rank.containsKey(term))continue;

                    //calculate term frequency
                    int tf = 0;
                    int fromIndex = 0;
                    while ((fromIndex = chapter.indexOf(term, fromIndex)) != -1 ){
                        tf++;
                        fromIndex++;
                    }

                    //calculate importance
                    rank.put(term,(double)tf);
                }
            }

            //PART 3

            //sort the terms by importance (descending)
            List<Map.Entry<String, Double>> list;
            LinkedHashMap<String, Double> rankSorted;
            for(int i=0;i<rankPerChapter.size();i++){
                HashMap<String, Double> rank = rankPerChapter.get(i);
                list = new ArrayList<>(rank.entrySet());
                list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

                rankSorted = new LinkedHashMap<>();
                for (Map.Entry<String, Double> entry : list) {
                    rankSorted.put(entry.getKey(), entry.getValue());
                }
                rankPerChapter.set(i,rankSorted);
            }

            //tokenize without stemming
            StandardAnalyzer analyzer2 = new StandardAnalyzer(EnglishAnalyzer.getDefaultStopSet());
            TokenStream tokenStream2;
            String[] chaptersTokenizedNotStemmed = new String[chapters.length];
            for(int i=0;i<chapters.length;i++){
                tokenStream2
                        = analyzer2.tokenStream("contents",
                        new StringReader(chapters[i]));

                CharTermAttribute attribute
                        = tokenStream2.addAttribute(CharTermAttribute.class);
                String result = "";
                tokenStream2.reset();
                while (tokenStream2.incrementToken()) {
                    result += attribute.toString()+" ";
                }
                chaptersTokenizedNotStemmed[i] = result;
                tokenStream2.close();
            }

            //print most important full words for one chapter
            HashMap<String, Double> rank = rankPerChapter.get(moduleIndex);
            String chapter = chaptersTokenizedNotStemmed[moduleIndex];
            String[] chapterWords = chapter.split(" ");
            for (String key : rank.keySet()) {
                String fullWords = "";
                for(int i=0;i<chapterWords.length;i++){
                    if(chapterWords[i].startsWith(key) && fullWords.indexOf(chapterWords[i]) == -1){
                        fullWords += (chapterWords[i] + " ");
                    }
                }
                //System.out.println(fullWords + " - " + rank.get(key));
                rankFullWords.put(fullWords,rank.get(key));
            }
        } catch (IOException e){
            e.printStackTrace();
        }
        return rankFullWords;
    }

    //returns all Skills
    public static List<Skill> getSkills(){
        List<Skill> skills = new ArrayList<>();
        try {
            CSVReader reader = new CSVReader(new FileReader("src/main/java/main/skills_en.csv"));
            String [] nextLine;

            int lineIndex = 0;
            while ((nextLine = reader.readNext()) != null) {
                if(lineIndex < 2){
                    lineIndex++;
                    continue;//skip first 2 lines
                }
                Skill skill = new Skill(nextLine[1],nextLine[4],nextLine[5]);
                skills.add(skill);
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return skills;
    }

    /*
    Return the matching skills for a module
    for the 10 most important words of the module
    count how many of the words appear in the skill description
    sort the skills descending
    */
    public static List<Skill> getSkillsForModule (int moduleIndex){
        List<Skill> skillsAll = getSkills();
        HashMap<String,Double> termsRank = getMostImportantTerms(moduleIndex);
        HashMap<String,Double> skillsRank = new HashMap();
        double termsInSkillCount;

        for(Skill skill : skillsAll){
            termsInSkillCount = 0;

            String altLabels = skill.altLabels;
            //first 10 terms
            List<Map.Entry<String, Double>> termEntries = new ArrayList<>(termsRank.entrySet());
            int termIndex = 0;
            for (Map.Entry<String, Double> entry : termEntries) {
                if(termIndex == 10)break;
                String fullTerms = entry.getKey();
                String[] fullTermsArray = fullTerms.split(" ");

                for(String term : fullTermsArray){
                    if(altLabels.contains(term))termsInSkillCount++;
                }

                termIndex++;
            }
            skillsRank.put(skill.conceptUri,termsInSkillCount);
        }

        LinkedHashMap<String,Double>  skillSorted = sortHashmapDesc(skillsRank);
        List<Skill> skills = new ArrayList();
        for (Map.Entry<String,Double> entry : skillSorted.entrySet()){
            for(Skill skill: skillsAll){
                if(skill.conceptUri == entry.getKey()){
                    skills.add(skill);
                    break;
                }
            }
        }

        return skills;
    }

    //sort hashmap descending
    public static LinkedHashMap sortHashmapDesc(HashMap<String,Double> map){
        List<Map.Entry<String, Double>> list;
        LinkedHashMap<String,Double> mapSorted = new LinkedHashMap();

        list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        for (Map.Entry<String,Double> entry : list) {
            mapSorted.put(entry.getKey(), entry.getValue());
        }
        return mapSorted;
    }
}