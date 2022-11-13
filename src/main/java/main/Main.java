package main;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    public static void main(String[] args) {

        try {
            //PDF to text
            PDDocument doc = PDDocument.load(new File("src/main/java/main/modules.pdf"));
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(doc);
            Files.write(Paths.get("src/main/java/main/result.txt"), text.getBytes());

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
                    result += attribute.toString()+" ";
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

                    //calculate tf
                    int tf = 0;
                    int fromIndex = 0;
                    while ((fromIndex = chapter.indexOf(term, fromIndex)) != -1 ){
                        tf++;
                        fromIndex++;
                    }

                    //calculate idf
                    int idf = 0;
                    for(String chapter2 : chaptersTokenized){
                        if(chapter2.indexOf(term) != -1){
                            idf++;
                        }
                    }

                    //calculate importance
                    double importance = (double)tf/(double)idf;
                    rank.put(term,importance);
                }
            }

            //sort the terms by importance (descending)
            List<Map.Entry<String, Double>> list;
            HashMap<String, Double> rankSorted;
            for(int i=0;i<rankPerChapter.size();i++){
                HashMap<String, Double> rank = rankPerChapter.get(i);
                list = new ArrayList<>(rank.entrySet());
                list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

                rankSorted = new HashMap<>();
                for (Map.Entry<String, Double> entry : list) {
                    rankSorted.put(entry.getKey(), entry.getValue());
                }
                rankPerChapter.set(i,rankSorted);
            }

            //test - print most important terms for one chapter
            HashMap<String, Double> rank = rankPerChapter.get(5);
            for (String key : rank.keySet()) {
                System.out.println(key + " - " + rank.get(key));
            }

        } catch (IOException e){
            e.printStackTrace();
        }
    }
}