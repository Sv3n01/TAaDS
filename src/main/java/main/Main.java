package main;

import com.jayway.jsonpath.JsonPath;
import com.opencsv.CSVReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.lucene.analysis.en.EnglishAnalyzer;

import java.net.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static double tfIdfThreshold = 0.019;//0.019
    private static String escoApiQuery;
    private static String[] courses = {
            "(1) Agile Software Development",
            "(2) Computer Graphics 1",
            "(3) Computational Intelligence",
            "(4) Distributed Systems",
            "(5) IT Security",
            "(6) Mobile Systems",
            "(7) Service-oriented Networks",
            "(8) Signals & Systems",
            "(9) Web Applications ",
            "(10) IT-Security (adv. chapters)",
            "(11) Distributed Systems Advanced Chapters",
            "(12) Semantic Technologies in Distributed Systems",
            "(13) Software Quality",
            "(14) Text Mining Search",
            "(15) eBusiness",
            "(16) Human-Computer Interaction",
            "(17) Image Processing 1",
            "(18) Image Processing 2 ",
            "(19) Media Production 1",
    };

    public static void main(String[] args) {
        int n;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));;

        for(String course : courses){
            System.out.println(course);
        }
        System.out.print("\nEnter Course number: ");
        try {
            n = Integer.parseInt(br.readLine());
            n--;//index
            System.out.print("\n\n---"+courses[n]+"---\n\n");
            printEscoEntries(n);

            System.out.print("\nAgain? (y/n): ");
            String s = br.readLine();
            System.out.print("\n");
            while(s.equals("y")){
                for(String course : courses){
                    System.out.println(course);
                }
                System.out.print("\nEnter Course number: ");
                n = Integer.parseInt(br.readLine());
                n--;//index
                System.out.print("\n\n---"+courses[n]+"---\n\n");
                printEscoEntries(n);

                System.out.print("\nAgain? (y/n): ");
                s = br.readLine();
            }
        } catch(NumberFormatException nfe) {
            System.err.println("Invalid Format!");
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    public static void printTerms(int moduleIndex){
        LinkedHashMap terms = getMostImportantTerms(moduleIndex);
        Set<Map.Entry<String,Double>> termEntries = terms.entrySet();
        for(Map.Entry<String,Double> entry : termEntries){
            if(entry.getValue() > tfIdfThreshold){
                System.out.println(entry.getKey() + " - " + entry.getValue());
            }
        }
    }

    public static void printEscoEntries(int moduleIndex){
        List<EscoEntry> escoEntries = getEscoEntriesFromApi(moduleIndex);

        System.out.println("--- Most Relevant Terms ---");
        System.out.println(escoApiQuery);

        //print Skills
        System.out.println("\n--- SKILLS ---");
        for(EscoEntry escoEntry : escoEntries){
            if(escoEntry.type.equals("Skill")){
                System.out.println(escoEntry.preferredLabel);
            }
        }
        System.out.println("\n\n--- OCCUPATIONS ---");
        for(EscoEntry escoEntry : escoEntries){
            if(escoEntry.type.equals("Occupation")){
                System.out.println(escoEntry.preferredLabel);
            }
        }

        System.out.println("\n\n--- CONCEPTS ---");
        for(EscoEntry escoEntry : escoEntries){
            if(escoEntry.type.equals("Concept")){
                System.out.println(escoEntry.preferredLabel);
            }
        }
    }

    public static List<EscoEntry> getEscoEntriesFromApi(int moduleIndex){
        List<EscoEntry> escoEntries = new ArrayList<>();
        String query = getApiQuery(moduleIndex);
        escoApiQuery = query;//save for other functions
        try {
            query = URLEncoder.encode(query, "UTF-8");
            URL obj = new URL("https://ec.europa.eu/esco/api/search?text="+query+"&language=en");
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Accept", "application/json,application/json;charset=UTF-8");
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                String json = response.toString();
                Integer resultCount = JsonPath.read(json, "$._embedded.results.length()");
                for(int i=0; i < resultCount; i++) {
                    String preferredLabel = JsonPath.read(json, "$._embedded.results[" + i + "].title");
                    String uri = JsonPath.read(json, "$._embedded.results[" + i + "].uri");
                    String type = JsonPath.read(json, "$._embedded.results[" + i + "].className");
                    EscoEntry escoEntry = new EscoEntry(uri,preferredLabel,"",type);
                    escoEntries.add(escoEntry);
                }
            } else {
                System.out.println("GET request did not work.");
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return escoEntries;
    }

    //get the search string for the esco api
    public static String getApiQuery(int moduleIndex){
        LinkedHashMap terms = getMostImportantTerms(moduleIndex);
        Set<Map.Entry<String,Double>> termEntries = terms.entrySet();
        String escoSearchString = "";
        for(Map.Entry<String,Double> entry : termEntries){
            if(entry.getValue() > tfIdfThreshold){
                escoSearchString += entry.getKey() + " ";
            }
        }
        return escoSearchString;
    }

    //returns hashmap key: full words string, value: tfidf score of the word stem
    public static LinkedHashMap<String,Double> getMostImportantTerms(int moduleIndex){
        LinkedHashMap<String, Double> rankFullWords = new LinkedHashMap<>();
        try {
            //PDF to text
            PDDocument doc = PDDocument.load(new File("src/main/java/main/modules.pdf"));
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(doc);
            Files.write(Paths.get("src/main/java/main/modules.txt"), text.getBytes());

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
                    String stem = attribute.toString();
                    //Filter out invalid stems
                    Boolean validStem = true;
                    if(stem.length() < 4){//min. 4 letters
                        validStem = false;
                    }
                    //only letters (no numbers,special characters)
                    Pattern digit = Pattern.compile("[0-9]");
                    Pattern special = Pattern.compile ("[!@#$%&*()_+=|<>?{}.\\[\\]~-]");
                    Matcher hasDigit = digit.matcher(stem);
                    Matcher hasSpecial = special.matcher(stem);
                    if(hasDigit.find() || hasSpecial.find()){
                        validStem = false;
                    }

                    if(validStem){
                        result += stem+" ";
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

                    /*
                    tf(t,d) = n/N
                    n is the number of times term t appears in the document d.
                    N is the total number of terms in the document d.
                    */
                    double n = 0;
                    int fromIndex = 0;
                    while ((fromIndex = chapter.indexOf(term, fromIndex)) != -1 ){
                        n++;
                        fromIndex++;
                    }

                    double N = terms.length;
                    double tf = n/N;

                    /*
                    idf(t,D) = log (N/( n))
                    N is the number of documents in the data set.
                    n is the number of documents that contain the term t among the data set.
                    */
                    N = chapters.length;
                    n = 0;
                    for(String chapterTokenized : chaptersTokenized){
                        if(chapterTokenized.indexOf(term) != -1){
                            n++;
                        }
                    }
                    double idf = Math.log(N/n);

                    //tf * idf is in range from 0 to 1
                    double tfidf = tf * idf;


                    //calculate importance
                    rank.put(term,tfidf);
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

            //create the most important full words for one chapter
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

    //Deprecated. Use api now.
    //returns all esco entries
   /* public static List<EscoEntry> getEscoEntries(){
        List<EscoEntry> escoEntries = new ArrayList<>();
        try {
            CSVReader reader = new CSVReader(new FileReader("src/main/java/main/esco_entries_en.csv"));
            String [] nextLine;

            int lineIndex = 0;
            while ((nextLine = reader.readNext()) != null) {
                if(lineIndex < 2){
                    lineIndex++;
                    continue;//skip first 2 lines
                }
                EscoEntry escoEntry = new EscoEntry(nextLine[1],nextLine[4],nextLine[5],"");
                escoEntries.add(escoEntry);
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return escoEntries;
    }*/

    /*
    Deprecated. Use api now.
    Return the matching esco entries for a module
    for the 10 most important words of the module
    count how many of the words appear in the esco entry description
    sort the esco entries descending
    */
    /*public static List<EscoEntry> getEscoEntriesForModule (int moduleIndex){
        List<EscoEntry> escoEntriesAll = getEscoEntries();
        HashMap<String,Double> termsRank = getMostImportantTerms(moduleIndex);
        HashMap<String,Double> escoEntriesRank = new HashMap();
        double escoEntryScore;

        for(EscoEntry escoEntry : escoEntriesAll){
            escoEntryScore = 0;

            String altLabels = escoEntry.altLabels;
            //first 10 terms
            List<Map.Entry<String, Double>> termEntries = new ArrayList<>(termsRank.entrySet());
            int termIndex = 0;
            for (Map.Entry<String, Double> entry : termEntries) {
                if(termIndex == 10)break;
                String fullTerms = entry.getKey();
                String[] fullTermsArray = fullTerms.split(" ");

                for(String term : fullTermsArray){
                    if(Arrays.asList(altLabels.split(" ")).contains(term))escoEntryScore += entry.getValue();
                }

                termIndex++;
            }
            escoEntriesRank.put(escoEntry.conceptUri,escoEntryScore);
        }

        LinkedHashMap<String,Double>  escoEntriesSorted = sortHashmapDesc(escoEntriesRank);
        List<EscoEntry> escoEntries = new ArrayList();
        for (Map.Entry<String,Double> entry : escoEntriesSorted.entrySet()){
            for(EscoEntry escoEntry : escoEntriesAll){
                if(escoEntry.conceptUri == entry.getKey()){
                    escoEntries.add(escoEntry);
                    break;
                }
            }
        }

        return escoEntries;
    }*/

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