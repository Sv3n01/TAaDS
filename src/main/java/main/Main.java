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
import java.util.logging.Level;
import java.util.logging.Logger;
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
        Logger apacheLogger = java.util.logging.Logger.getLogger("org.apache.pdfbox.pdmodel.font.PDTrueTypeFont");
        apacheLogger.setLevel(Level.OFF);
        apacheLogger = java.util.logging.Logger.getLogger("org.apache.pdfbox.cos.COSDocument");
        apacheLogger.setLevel(Level.OFF);


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
        List<EscoEntry> escoEntries = getEscoEntriesFromApiV2(moduleIndex, "skill");

        System.out.println("--- Most Relevant Terms ---");
        System.out.println(escoApiQuery);

        System.out.println("\n--- SKILLS ---");
        for(EscoEntry escoEntry : escoEntries){
            System.out.println(escoEntry.preferredLabel);
        }

        escoEntries = getEscoEntriesFromApiV2(moduleIndex, "occupation");
        System.out.println("\n\n--- OCCUPATIONS ---");
        for(EscoEntry escoEntry : escoEntries){
            System.out.println(escoEntry.preferredLabel);
        }

        escoEntries = getEscoEntriesFromApiV2(moduleIndex, "concept");
        System.out.println("\n\n--- CONCEPTS ---");
        for(EscoEntry escoEntry : escoEntries){
            System.out.println(escoEntry.preferredLabel);
        }
    }

    public static List<EscoEntry> getEscoEntriesFromApi(int moduleIndex, String type){
        List<EscoEntry> escoEntries = new ArrayList<>();
        String query = getApiQuery(moduleIndex);
        escoApiQuery = query;//save for other functions
        try {
            query = URLEncoder.encode(query, "UTF-8");
            URL obj = new URL("https://ec.europa.eu/esco/api/search?type="+type+"&text="+query+"&language=en");
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
                Boolean isDigitalSkill;
                for(int i=0; i < resultCount; i++) {
                    String uri = JsonPath.read(json, "$._embedded.results[" + i + "].uri");
                    if(type.equals("skill")){
                        isDigitalSkill = false;
                        for(String digitalUri : digitalSkillUris){
                            if(digitalUri.contains(uri)){
                                isDigitalSkill = true;
                                break;
                            }
                        }
                        if(!isDigitalSkill)continue;
                    }
                    String preferredLabel = JsonPath.read(json, "$._embedded.results[" + i + "].title");
                    String typeJson = JsonPath.read(json, "$._embedded.results[" + i + "].className");
                    EscoEntry escoEntry = new EscoEntry(uri,preferredLabel,"",typeJson);
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
        LinkedHashMap terms = getApiQueryWithScore(moduleIndex);
        Set<Map.Entry<String,Double>> termEntries = terms.entrySet();
        String escoSearchString = "";
        for(Map.Entry<String,Double> entry : termEntries){
            escoSearchString += entry.getKey() + " ";
        }
        return escoSearchString;
    }

    //get the words for the search string with score
    public static LinkedHashMap<String,Double> getApiQueryWithScore(int moduleIndex){
        LinkedHashMap terms = getMostImportantTerms(moduleIndex);
        LinkedHashMap<String,Double> queryTerms = new LinkedHashMap();
        Set<Map.Entry<String,Double>> termEntries = terms.entrySet();
        for(Map.Entry<String,Double> entry : termEntries){
            if(entry.getValue() > tfIdfThreshold){
                queryTerms.put(entry.getKey(), entry.getValue());
            }
        }
        return queryTerms;
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

                    //only letters (no numbers,special characters)
                    Pattern digit = Pattern.compile("[0-9]");
                    Pattern special = Pattern.compile ("[!@#$%&*()_+=|<>?{}.\\[\\]~]");//-
                    Matcher hasDigit = digit.matcher(stem);
                    Matcher hasSpecial = special.matcher(stem);
                    if(hasDigit.find() || hasSpecial.find()){
                        validStem = false;
                    }

                    //further stopwords
                    if(stem.equalsIgnoreCase("about") || stem.equalsIgnoreCase("Berlin")){
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
                    //only letters (no numbers,special characters)
                    Pattern digit = Pattern.compile("[0-9]");
                    Pattern special = Pattern.compile ("[!@#$%&*()_+=|<>?{}.\\[\\]~]");//-
                    Matcher hasDigit = digit.matcher(chapterWords[i]);
                    Matcher hasSpecial = special.matcher(chapterWords[i]);
                    if(!hasDigit.find() && !hasSpecial.find()){
                        if(chapterWords[i].startsWith(key) && fullWords.indexOf(chapterWords[i]) == -1){
                            fullWords += (chapterWords[i] + " ");
                        }
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

    //with sorting after retrieval
    public static List<EscoEntry> getEscoEntriesFromApiV2(int moduleIndex, String type){
        List<EscoEntry> entriesApi = getEscoEntriesFromApi(moduleIndex, type);
        List<EscoEntry> entriesCsv = getEscoEntriesCSV();
        //replace entriesApi by CSV file entries for more data
        int apiIndex = 0;
        for(EscoEntry entry : entriesApi){
            for(EscoEntry entry2 : entriesCsv){
                if(entry2.preferredLabel.equals(entry.preferredLabel)){
                    entry2.type = entry.type;//more exact
                    entriesApi.set(apiIndex,entry2);
                }
            }
            apiIndex++;
        }

        //calculate score for entriesApi
        LinkedHashMap<String,Double> queryTermsScore = getApiQueryWithScore(moduleIndex);
        HashMap<Object,Double> entriesScored = new HashMap<>();
        double score;
        for(EscoEntry entry: entriesApi){
            score = 0.0;
            String entryText = entry.altLabels + " "; //+ entry.description;
            String[] entryTextSplit = entryText.split(" |\n");
            List<Map.Entry<String, Double>> queryTermsList = new ArrayList<>(queryTermsScore.entrySet());
            for(Map.Entry<String,Double> queryEntry : queryTermsList){
                String[] queryTermsSplit = queryEntry.getKey().split(" ");
                outerLoop:
                for(String queryEntrySplit : queryTermsSplit){
                    for(String entryWord : entryTextSplit){
                        if(entryWord.equalsIgnoreCase(queryEntrySplit)){
                            score += queryEntry.getValue();
                            break outerLoop;
                        }
                    }
                }
            }

            entriesScored.put(entry,score);
        }

        LinkedHashMap<EscoEntry,Double> entriesScoredSorted = sortHashmapDesc(entriesScored);
        List<Map.Entry<EscoEntry, Double>> result1 = new ArrayList<>(entriesScoredSorted.entrySet());
        List<EscoEntry> result2 = new LinkedList<>();
        for(Map.Entry<EscoEntry, Double> entry : result1){
            result2.add(entry.getKey());
        }
        return result2;
    }


    //returns all esco entries from csv file
   public static List<EscoEntry> getEscoEntriesCSV(){
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
                EscoEntry escoEntry = new EscoEntry(nextLine[1],nextLine[4],nextLine[5],"skill");
                escoEntry.description = nextLine[12];
                escoEntries.add(escoEntry);
            }

        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return escoEntries;
    }

    //sort hashmap descending
    public static LinkedHashMap sortHashmapDesc(HashMap<Object,Double> map){
        List<Map.Entry<Object, Double>> list;
        LinkedHashMap<Object,Double> mapSorted = new LinkedHashMap();

        list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        for (Map.Entry<Object,Double> entry : list) {
            mapSorted.put(entry.getKey(), entry.getValue());
        }
        return mapSorted;
    }

    private static String[] digitalSkillUris = {"\"http://data.europa.eu/esco/skill/dad7e408-162f-46a4-8567-db560e19e2fc,\n",
            "http://data.europa.eu/esco/skill/43dfbe7f-9e10-4871-b171-e5076737b4cf",
            "http://data.europa.eu/esco/skill/2d6d4784-0992-48e7-b100-22d7229b3f0a",
            "http://data.europa.eu/esco/skill/2ef6bebc-2133-47df-b26a-f38f51b0f867",
            "http://data.europa.eu/esco/skill/af1d906a-c13d-4de1-a362-6980e6dc9937",
            "http://data.europa.eu/esco/skill/5fd5c985-eaaa-47aa-8314-62359c54505a",
            "http://data.europa.eu/esco/skill/696e3b5b-8b61-45af-ae4c-3ab700f197ec",
            "http://data.europa.eu/esco/skill/2973b3bc-5893-4b3d-8953-22ef677a162f",
            "http://data.europa.eu/esco/skill/694f1a0a-b6bd-453f-af6d-a833503da307",
            "http://data.europa.eu/esco/skill/f6424ebb-8401-446b-9f7e-10d7c854da02",
            "http://data.europa.eu/esco/skill/8975edb1-d2cb-4c49-a3c0-3b21a9ab8797",
            "http://data.europa.eu/esco/skill/d9818afc-1dec-4944-8c9c-e3ae8fad176d",
            "http://data.europa.eu/esco/skill/9d0d89be-bffa-4393-b6f6-8d05bea49051",
            "http://data.europa.eu/esco/skill/6c08403c-a5bb-4868-b8c2-b7d039c0e511",
            "http://data.europa.eu/esco/skill/42c3287f-5e50-4559-a2d1-2000c70e0aab",
            "http://data.europa.eu/esco/skill/fd6d2981-3d4a-4ce2-9741-cfc98c5b74bd",
            "http://data.europa.eu/esco/skill/a3882117-5ace-40b3-ad39-014d8b53c835",
            "http://data.europa.eu/esco/skill/e2b82b00-f07f-4fc8-a7bb-c8febf65efe5",
            "http://data.europa.eu/esco/skill/3093881b-74f6-48e1-820e-50831b0d0775",
            "http://data.europa.eu/esco/skill/3cfa46e9-b7da-44ac-b09e-9596dffe0425",
            "http://data.europa.eu/esco/skill/8e46491b-7ae4-4add-bb6f-b8854e73c914",
            "http://data.europa.eu/esco/skill/d6c29154-9cc1-48a4-a2de-3d1d0d7f0831",
            "http://data.europa.eu/esco/skill/fdfa3dcb-b8f0-454f-a659-64c66dc6b6a2",
            "http://data.europa.eu/esco/skill/a91732ce-988e-4105-9570-f425c6ffdc82",
            "http://data.europa.eu/esco/skill/ba93b71b-c433-4a01-8246-ed2daa85a9a2",
            "http://data.europa.eu/esco/skill/09cf2c34-6f93-4a19-a9f1-c0d866414bcc",
            "http://data.europa.eu/esco/skill/5e0d6552-7efb-4207-b805-68fed752d778",
            "http://data.europa.eu/esco/skill/d6e3395b-5ab6-4fba-969a-3df634e904c5",
            "http://data.europa.eu/esco/skill/5be3d306-6cf1-4b49-aa1d-01651dd4ba4c",
            "http://data.europa.eu/esco/skill/034c29fa-c3ba-45ea-b8f3-bf8e3705e386",
            "http://data.europa.eu/esco/skill/f4229545-9cd3-4abf-ac7b-744040a7a61b",
            "http://data.europa.eu/esco/skill/7369f779-4b71-4aab-8836-48b69c676eec",
            "http://data.europa.eu/esco/skill/1d6c7de4-350e-4868-a47b-333b4b0d9650",
            "http://data.europa.eu/esco/skill/0dc7aa1b-b5af-4f4b-81ce-ee22ed8b6926",
            "http://data.europa.eu/esco/skill/9190d87f-9792-42e0-bb7f-64294a656bcd",
            "http://data.europa.eu/esco/skill/6417a4cc-6f61-4459-a114-761a7fa0279d",
            "http://data.europa.eu/esco/skill/f2741507-c5f7-45b6-8bcd-8d0d27650d68",
            "http://data.europa.eu/esco/skill/dad18fbb-7053-46ed-8a7e-817a10b26ab8",
            "http://data.europa.eu/esco/skill/f0de4973-0a70-4644-8fd4-3a97080476f4",
            "http://data.europa.eu/esco/skill/6468e5fb-f3be-4025-87be-4b6556755c61",
            "http://data.europa.eu/esco/skill/c2999f0c-eb37-4cdf-b9b0-82107b628794",
            "http://data.europa.eu/esco/skill/f771d20c-815b-43e1-9355-b7f3f63a0b23",
            "http://data.europa.eu/esco/skill/ab1e97ed-2319-4293-a8b7-072d2648822f",
            "http://data.europa.eu/esco/skill/f5750c05-7b15-4e0b-8591-f9a1bb95aaac",
            "http://data.europa.eu/esco/skill/82312096-4df8-49ce-9cdd-a6e08af56b45",
            "http://data.europa.eu/esco/skill/0cd6dcf1-5778-42a5-b685-4d01ae4a4871",
            "http://data.europa.eu/esco/skill/a9b68e58-766a-403d-b5fe-955b57bdad1b",
            "http://data.europa.eu/esco/skill/4b88b1ee-c2d9-473a-9fe8-ba3b9c0c179a",
            "http://data.europa.eu/esco/skill/23900e3d-06fa-424c-a5dc-c0ea42edde1e",
            "http://data.europa.eu/esco/skill/ab49f767-296b-47d5-af56-0b4a69515b03",
            "http://data.europa.eu/esco/skill/1b2889c2-154d-4e5f-b7d1-bd1f07df86ab",
            "http://data.europa.eu/esco/skill/3c4c3fa1-d638-42df-8e98-2a38021da6f9",
            "http://data.europa.eu/esco/skill/9faa44df-0821-4f69-8f86-184c799cf44f",
            "http://data.europa.eu/esco/skill/a800a15c-2d1b-4151-8bf3-b21d47f709a1",
            "http://data.europa.eu/esco/skill/0a9acb6b-1139-4be9-b431-3a80a959f2f4",
            "http://data.europa.eu/esco/skill/cfb8d64c-f8d7-4894-a02e-2551ab33f1d0",
            "http://data.europa.eu/esco/skill/fcb5e5c1-69b4-46d7-b9e6-328a5381dd89",
            "http://data.europa.eu/esco/skill/2b34764d-637c-48f5-aa70-31e15c965db6",
            "http://data.europa.eu/esco/skill/e975b791-b488-4935-be44-06f2f9a443bb",
            "http://data.europa.eu/esco/skill/63206320-8fb8-481d-b969-5ebdd33ac32e",
            "http://data.europa.eu/esco/skill/c5e8abde-d2ba-4e8e-a65e-720b71180666",
            "http://data.europa.eu/esco/skill/a2b566b0-1070-47a4-adc8-88839942ce25",
            "http://data.europa.eu/esco/skill/7193cb6d-8334-494f-86e5-21e6d03a47c3",
            "http://data.europa.eu/esco/skill/b16200f4-4f39-4b8b-aa9d-3568054d6bdb",
            "http://data.europa.eu/esco/skill/1d86f05e-e9cc-40ce-99d8-2b21cc71b16b",
            "http://data.europa.eu/esco/skill/e9ac6d35-0219-44c3-b488-bfd14923d09f",
            "http://data.europa.eu/esco/skill/3497ebe1-d417-4105-a68f-f7bbcac7ee5f",
            "http://data.europa.eu/esco/skill/e043aeeb-78f5-4049-afbf-12bba52225bc",
            "http://data.europa.eu/esco/skill/8369ede5-4200-4a44-be2e-bc60ef959259",
            "http://data.europa.eu/esco/skill/7edd11f2-6b4a-4a76-8b82-b5c57f3d8f07",
            "http://data.europa.eu/esco/skill/1973c966-f236-40c9-b2d4-5d71a89019be",
            "http://data.europa.eu/esco/skill/945b8655-5577-4c3a-946a-842d4527afa8",
            "http://data.europa.eu/esco/skill/3325729c-96db-4217-a9ca-4564cc1f909b",
            "http://data.europa.eu/esco/skill/258d3de5-60fd-4336-aa7e-7700b64f5ed5",
            "http://data.europa.eu/esco/skill/e1e99428-b91b-45e1-8ede-ed0cbf161930",
            "http://data.europa.eu/esco/skill/f39336a8-8bf3-4a36-ac40-2f88836f5112",
            "http://data.europa.eu/esco/skill/bf298db2-b90e-4739-85ca-9c3003103fab",
            "http://data.europa.eu/esco/skill/97bd1c21-66b2-4b7e-ad0f-e3cda590e378",
            "http://data.europa.eu/esco/skill/a4d336a6-9ffd-402a-91cc-f359716ba4e0",
            "http://data.europa.eu/esco/skill/4463a721-69f3-413d-8321-43e3af13a4f1",
            "http://data.europa.eu/esco/skill/fc461d64-ef74-43d5-8fc1-e1d1943e5bc5",
            "http://data.europa.eu/esco/skill/b3a186be-5f1b-45d3-a468-41183e9780f3",
            "http://data.europa.eu/esco/skill/61d6ef20-6cc7-40f8-919e-3360db6cf2b3",
            "http://data.europa.eu/esco/skill/c3e36d05-8ae8-447f-bb2b-6f9409f85389",
            "http://data.europa.eu/esco/skill/a9dc215e-4e26-4ac0-b1e8-a721a88af813",
            "http://data.europa.eu/esco/skill/6474fc36-67ae-436b-9d39-c14402c6a89b",
            "http://data.europa.eu/esco/skill/c4949dcf-bbcc-40f2-8584-6c6299beb7fb",
            "http://data.europa.eu/esco/skill/a96dea4e-1c76-4808-8f18-3ce662ff82cb",
            "http://data.europa.eu/esco/skill/5d74614d-32c8-4ca4-9818-6980e52424b1",
            "http://data.europa.eu/esco/skill/a3bd38a7-06e4-4e9c-8bbc-75aa36871801",
            "http://data.europa.eu/esco/skill/b16bcbcb-1d3f-42b3-a6a5-b91348a72b70",
            "http://data.europa.eu/esco/skill/2f6e5619-2478-475f-a414-ec05c2b72795",
            "http://data.europa.eu/esco/skill/e87ec79a-c9ff-46f5-84fa-7a0f394cdf40",
            "http://data.europa.eu/esco/skill/7557f0f4-b8bc-4ac0-b91e-222ab1ba744f",
            "http://data.europa.eu/esco/skill/d86595cb-0c68-4d98-aea8-32269da7eef5",
            "http://data.europa.eu/esco/skill/0fba68dc-6f5d-4974-b3cf-5d3215f7d363",
            "http://data.europa.eu/esco/skill/d4ae4774-a81d-4255-8491-cade3580685a",
            "http://data.europa.eu/esco/skill/e13b734a-218c-434a-a7cc-febac6d24ef3",
            "http://data.europa.eu/esco/skill/a7723f06-53c3-4228-9fd0-1952ea1d7391",
            "http://data.europa.eu/esco/skill/4b244020-5f28-4e20-b74f-1c2dcb6f15cb",
            "http://data.europa.eu/esco/skill/95d84b77-ba94-492f-a1e6-18be13f16cf0",
            "http://data.europa.eu/esco/skill/ed392b45-1087-44a7-89a9-d514224639ff",
            "http://data.europa.eu/esco/skill/7abf5b24-8f18-49c5-a64b-5a5870a03a56",
            "http://data.europa.eu/esco/skill/2afb2b59-c9a3-4cf3-b1dd-1a2fad51e583",
            "http://data.europa.eu/esco/skill/79c366d4-0316-4838-b72e-c3be4f90fbd3",
            "http://data.europa.eu/esco/skill/5a29fbb3-3b5c-44f2-b07a-e84faa790088",
            "http://data.europa.eu/esco/skill/2b92a5b2-6758-4ee3-9fb4-b6387a55cc8f",
            "http://data.europa.eu/esco/skill/6b222aac-688c-4c9d-acbc-7fe25d37565e",
            "http://data.europa.eu/esco/skill/21dd999b-c5cc-4cec-8a9f-33f07079c12e",
            "http://data.europa.eu/esco/skill/b36176ec-3499-4235-a15c-cb485a386dc6",
            "http://data.europa.eu/esco/skill/ffc1e455-ced2-4e67-bdb3-1c50f9683859",
            "http://data.europa.eu/esco/skill/b7b381f5-7de2-4f91-9a8f-524041cb47d9",
            "http://data.europa.eu/esco/skill/51b18096-85e6-425b-bb15-03a74eeda0dc",
            "http://data.europa.eu/esco/skill/cbeb1221-10ed-4676-a2a1-576718977001",
            "http://data.europa.eu/esco/skill/4720fc66-0202-4a4b-bbe8-129d908e5a15",
            "http://data.europa.eu/esco/skill/76842746-cacb-4e6d-adc6-0656d75c9c51",
            "http://data.europa.eu/esco/skill/8542b62b-18b1-4c7f-9d4e-e717c10fbd72",
            "http://data.europa.eu/esco/skill/8a211052-4bec-49c2-9b4e-7914c37d21b4",
            "http://data.europa.eu/esco/skill/4cdbc76c-75d8-4e12-8653-4820c2d5446f",
            "http://data.europa.eu/esco/skill/dfff95c6-9758-4c04-a228-7eaa88dec6cd",
            "http://data.europa.eu/esco/skill/489b2571-ce18-4944-b452-ed1f54e55b68",
            "http://data.europa.eu/esco/skill/7d02180a-87d1-4e25-92e1-653309c173c4",
            "http://data.europa.eu/esco/skill/238343b1-7b51-42b3-a9ed-cf24d3a236e7",
            "http://data.europa.eu/esco/skill/27aedf16-e2e9-419d-9ad8-1c3100e0e941",
            "http://data.europa.eu/esco/skill/ec85cc63-4e24-4631-bf92-8789db2605c0",
            "http://data.europa.eu/esco/skill/094ef6aa-844f-44ce-8456-fdc49276bf58",
            "http://data.europa.eu/esco/skill/da6393d5-a53c-4863-abc7-51f36281d74e",
            "http://data.europa.eu/esco/skill/a50c6a0a-5171-4be6-aa2b-50fe4d025d45",
            "http://data.europa.eu/esco/skill/6e53fd99-b646-4327-9580-ac062ab21188",
            "http://data.europa.eu/esco/skill/6e4f75b4-c60f-4623-a9ba-760c8245753b",
            "http://data.europa.eu/esco/skill/2579f843-87e8-4a9f-83ce-1284b2218b56",
            "http://data.europa.eu/esco/skill/546a96b1-5f0a-4c80-b3c5-8ac0830d9f0e",
            "http://data.europa.eu/esco/skill/0dcbf7c5-bedf-4485-b16b-23df54443864",
            "http://data.europa.eu/esco/skill/e2d0daae-2aa1-40cc-99e2-b340b02f97d3",
            "http://data.europa.eu/esco/skill/d31fab87-2a7d-485c-b699-2901ca294b15",
            "http://data.europa.eu/esco/skill/625b57ee-29d4-425b-bf99-9fd54dd021fe",
            "http://data.europa.eu/esco/skill/0b0335f3-0aa1-491e-895e-81fc8774a300",
            "http://data.europa.eu/esco/skill/a572fd00-6735-428b-9fb1-fd2bf6262b21",
            "http://data.europa.eu/esco/skill/5b776f2d-cac1-437e-badd-9871876209a5",
            "http://data.europa.eu/esco/skill/82313e12-9d39-47d1-b7c9-78bbd6bb50e2",
            "http://data.europa.eu/esco/skill/c46cf6f9-8b1e-44b9-a529-f5774c9167dd",
            "http://data.europa.eu/esco/skill/455a7133-cf0f-4c74-a91f-fd35c0d25b79",
            "http://data.europa.eu/esco/skill/3233330f-bb93-47ea-93b4-ed903d05d9f1",
            "http://data.europa.eu/esco/skill/9889b795-d755-41b2-8afd-2e3c87a65a38",
            "http://data.europa.eu/esco/skill/d1e954cd-d290-4d80-97c0-7969960d2928",
            "http://data.europa.eu/esco/skill/e8171178-9a60-40a6-ad3b-510e10cfebad",
            "http://data.europa.eu/esco/skill/91aade3a-9427-4e80-998b-29492dcb9ffd",
            "http://data.europa.eu/esco/skill/4bf59dde-8a03-483f-976a-45764f1e6d6f",
            "http://data.europa.eu/esco/skill/ff6904ef-36e7-4890-8fb1-8353b299f047",
            "http://data.europa.eu/esco/skill/cbe2c304-1e7d-489a-97d9-a7d3e37a9db6",
            "http://data.europa.eu/esco/skill/9295d1c5-8d7d-423e-a6ce-dfbc1d991f88",
            "http://data.europa.eu/esco/skill/f9da1ca4-40f4-483a-92e6-0a0c6d078638",
            "http://data.europa.eu/esco/skill/ee8fee1a-75c1-43fb-b08d-779929f99249",
            "http://data.europa.eu/esco/skill/6b2f8802-0cbf-436e-a37f-ca55ede41bbc",
            "http://data.europa.eu/esco/skill/0620ecd1-e4b1-4dfa-972b-38a93e5ebd9d",
            "http://data.europa.eu/esco/skill/fef18610-c8ca-431b-9ca8-d3d0cdffde72",
            "http://data.europa.eu/esco/skill/f8f1f725-0105-41fa-9887-4df9e7a7af1e",
            "http://data.europa.eu/esco/skill/7d913551-e17a-40ba-baf7-48d0c3b12e50",
            "http://data.europa.eu/esco/skill/58888680-07cd-4baa-8eb1-f846bff7e2d4",
            "http://data.europa.eu/esco/skill/85616cc4-98bf-4355-9cf1-72ff11752848",
            "http://data.europa.eu/esco/skill/bb165899-d95f-4433-95f2-173fefd716d2",
            "http://data.europa.eu/esco/skill/bf675dff-ce7b-4f88-8a93-683a06e6617e",
            "http://data.europa.eu/esco/skill/7a6b4549-cd3e-46c7-8a40-9096520dff84",
            "http://data.europa.eu/esco/skill/27b913f0-3c52-448b-ad27-fc6ef08fb326",
            "http://data.europa.eu/esco/skill/5284ecee-93dd-4145-a3c0-584af836c69e",
            "http://data.europa.eu/esco/skill/89227433-950f-4d9d-b0b0-abc33356ab42",
            "http://data.europa.eu/esco/skill/a056851d-4a8b-4007-b0f6-8224ca1ec1c2",
            "http://data.europa.eu/esco/skill/90227e81-a1f1-47ae-8e8a-453fb5661f08",
            "http://data.europa.eu/esco/skill/4b51e76e-9e8a-455f-a289-5b48246345c4",
            "http://data.europa.eu/esco/skill/97965983-0da4-4902-9daf-d5cd2693ef73",
            "http://data.europa.eu/esco/skill/77bf865e-6360-424f-9d77-ce03918c7dcf",
            "http://data.europa.eu/esco/skill/86f7126d-97fd-4da5-be96-d1cf6ecd54e6",
            "http://data.europa.eu/esco/skill/4f7fa632-15a6-4739-930b-abbac8055a80",
            "http://data.europa.eu/esco/skill/f8e3425c-fe44-4ffb-bafe-0e20d91dadf4",
            "http://data.europa.eu/esco/skill/0a099c81-6a0c-4f00-bb3b-990d23ec03a5",
            "http://data.europa.eu/esco/skill/930c5458-9b5d-4438-bef6-fc69c2c08548",
            "http://data.europa.eu/esco/skill/12274664-b642-4945-b88f-d7787dc3c9dd",
            "http://data.europa.eu/esco/skill/9156104b-9bcb-4c22-aaa8-02e3e990ebc7",
            "http://data.europa.eu/esco/skill/d181042e-c531-4461-af7c-4071c53418fe",
            "http://data.europa.eu/esco/skill/3a33cec3-4b44-471a-8864-5cc8567a8b1f",
            "http://data.europa.eu/esco/skill/c0326e27-5fa8-4dca-aecb-ed89ab491513",
            "http://data.europa.eu/esco/skill/a28df07f-976c-4eb4-b29a-06161af39756",
            "http://data.europa.eu/esco/skill/a4346013-a967-4a58-a533-6b32ad1364c5",
            "http://data.europa.eu/esco/skill/1e7ed56a-6d6b-422f-962e-38543d150755",
            "http://data.europa.eu/esco/skill/8785ccda-6f9f-4cf3-9033-fda10653d0be",
            "http://data.europa.eu/esco/skill/d15ce60f-ddab-41ce-ba56-398451b77b60",
            "http://data.europa.eu/esco/skill/1adec82b-e32b-4acc-bd89-e7d61d387161",
            "http://data.europa.eu/esco/skill/6a876489-7ad8-4a13-b09c-c9b8e97a302e",
            "http://data.europa.eu/esco/skill/be80acfc-b6f2-4411-9b8d-b19d9cd2556a",
            "http://data.europa.eu/esco/skill/c7282ec3-9153-4b4b-b7de-777e3207b04e",
            "http://data.europa.eu/esco/skill/d12e9d68-84c5-444e-aa98-d36533bf03f6",
            "http://data.europa.eu/esco/skill/dfb9cec6-3ae3-408a-9d57-c44361bf7aaf",
            "http://data.europa.eu/esco/skill/4a466c62-db7d-4905-9a41-f23214810acb",
            "http://data.europa.eu/esco/skill/c9e96450-421d-48af-9ee6-d9c50e29afc2",
            "http://data.europa.eu/esco/skill/df12fd50-55dc-4cfd-a021-818880668789",
            "http://data.europa.eu/esco/skill/5865c47c-cf1a-4dcb-8871-d4a1ea4b047b",
            "http://data.europa.eu/esco/skill/b3fc5018-ff7b-46fd-be18-dd4148e9b241",
            "http://data.europa.eu/esco/skill/2af29864-c941-40a5-ae7b-2b6c4b92c0d7",
            "http://data.europa.eu/esco/skill/f0f2da78-ef60-4653-b2c8-ff0a7a9ea2f8",
            "http://data.europa.eu/esco/skill/07889c08-7220-47c8-96f7-6068fbea00dc",
            "http://data.europa.eu/esco/skill/9cf681c7-89ec-470c-b651-7fe03786f586",
            "http://data.europa.eu/esco/skill/00c04e40-35ea-4ed1-824c-82f936c8f876",
            "http://data.europa.eu/esco/skill/05b70efb-6b80-4f04-9a25-67cd317daf37",
            "http://data.europa.eu/esco/skill/5a6dda77-c09d-441a-8456-c6ca7e1bda06",
            "http://data.europa.eu/esco/skill/58d7a289-dafd-4363-833f-d1dc4140885e",
            "http://data.europa.eu/esco/skill/1a165a9b-2006-49e1-90b8-c0709933b4a3",
            "http://data.europa.eu/esco/skill/fb7aee4d-f6c6-4a87-8e9d-8e542b3771f8",
            "http://data.europa.eu/esco/skill/f9a6f35b-01a7-40c9-8b61-b6ee46f97272",
            "http://data.europa.eu/esco/skill/1ffac4ac-fda7-407d-8ed1-ca8f4a8dc146",
            "http://data.europa.eu/esco/skill/7d11f682-644f-44fa-ad08-d6b0d97d2f28",
            "http://data.europa.eu/esco/skill/c741be10-1d34-42ed-94fc-3e78791e4bd5",
            "http://data.europa.eu/esco/skill/22225017-a02e-4171-8775-64c3adc87512",
            "http://data.europa.eu/esco/skill/a520b743-8f40-43ac-a2d5-755899120844",
            "http://data.europa.eu/esco/skill/71f81e1a-e2ca-48d6-b7b8-7a34c56c51f8",
            "http://data.europa.eu/esco/skill/611ed16b-99bf-4840-9cab-f55d1d286e0a",
            "http://data.europa.eu/esco/skill/31f53419-4bce-4b10-986a-70a9c7ac41a3",
            "http://data.europa.eu/esco/skill/8fb72af6-c2a9-4b12-aaf0-f5dbd4a9db9e",
            "http://data.europa.eu/esco/skill/95c35c3a-035f-47c2-90cf-7e934d20fc08",
            "http://data.europa.eu/esco/skill/55cc1f3f-69fc-46ec-b24a-9708f6bd92b4",
            "http://data.europa.eu/esco/skill/df11ab22-3c40-4dc8-ab0c-e9f41e62449e",
            "http://data.europa.eu/esco/skill/ff25cbf0-6978-4c4c-887e-d4b185410d74",
            "http://data.europa.eu/esco/skill/c77db25e-bb51-4a10-b2ad-99b48b1b4a37",
            "http://data.europa.eu/esco/skill/c3a03c5a-c260-4c26-9b9a-873abb396f4d",
            "http://data.europa.eu/esco/skill/f0ff6f10-cbb4-44c0-acd5-ed17046b1396",
            "http://data.europa.eu/esco/skill/7deca286-1bd6-490e-851f-82d9d410d584",
            "http://data.europa.eu/esco/skill/a3e7ebba-5183-4df8-9203-35b9b9b8f4fa",
            "http://data.europa.eu/esco/skill/a46743b7-ff8c-49b6-99f7-7fa5bc241771",
            "http://data.europa.eu/esco/skill/79e29b8b-47d1-470d-b7b1-32506bfe7d9a",
            "http://data.europa.eu/esco/skill/0e667579-d045-4c6a-b0f3-230a61edd014",
            "http://data.europa.eu/esco/skill/e3eb64db-bc0b-4cc0-bd31-5feb94b2ef50",
            "http://data.europa.eu/esco/skill/6a536b36-c5c7-4d10-bf67-a4fb0f8549b2",
            "http://data.europa.eu/esco/skill/c9b6aa32-6c42-4903-8e5a-23e43edf03fc",
            "http://data.europa.eu/esco/skill/5b4ea415-3a3a-4d90-91d2-2d18ad196f4b",
            "http://data.europa.eu/esco/skill/1abcc511-ba5a-42e0-8d88-7efb39af4c36",
            "http://data.europa.eu/esco/skill/e46291c7-52b9-4174-bd59-178884861038",
            "http://data.europa.eu/esco/skill/8efa6a7a-6556-4cb8-908d-59d3b5c58d2f",
            "http://data.europa.eu/esco/skill/211e4a02-69a6-494e-b94a-93741e2a3a0b",
            "http://data.europa.eu/esco/skill/1bc718b6-ed90-4a9d-9fa7-8cec928a4581",
            "http://data.europa.eu/esco/skill/484df271-bb52-49f1-8f50-f19624bf4df2",
            "http://data.europa.eu/esco/skill/3140d979-2d6a-4c10-add9-f7091103829f",
            "http://data.europa.eu/esco/skill/25ee43a8-f9b0-4990-96cf-eabe627ff0cc",
            "http://data.europa.eu/esco/skill/a18b1d91-f0bd-495d-ba8e-d9532f117b89",
            "http://data.europa.eu/esco/skill/501537a7-9d63-491d-ae02-09f04767ff1e",
            "http://data.europa.eu/esco/skill/8a6394d1-f44f-4b0d-81d5-7201fc025ba8",
            "http://data.europa.eu/esco/skill/d52426a8-e84f-4652-a655-7a6195381fcd",
            "http://data.europa.eu/esco/skill/54971fec-1c2f-49c0-9396-2f0c9fa5c8c0",
            "http://data.europa.eu/esco/skill/3501ac95-4574-4420-92b7-67331e649462",
            "http://data.europa.eu/esco/skill/5475343c-5e75-415f-b431-17f3c20a347e",
            "http://data.europa.eu/esco/skill/0ab03c6c-281b-48f4-83e0-10eeb27a1bae",
            "http://data.europa.eu/esco/skill/0d168770-4d9c-4096-b048-a6577818d306",
            "http://data.europa.eu/esco/skill/e43084e8-6e3d-4649-93e9-2455ff53bc84",
            "http://data.europa.eu/esco/skill/605399ce-4736-4b41-b41e-92ecf2139454",
            "http://data.europa.eu/esco/skill/4959697d-3a24-4f46-af0c-b752318e6c54",
            "http://data.europa.eu/esco/skill/04f1b938-d4d4-4cb1-a863-982af76b9d93",
            "http://data.europa.eu/esco/skill/2e1888d8-97f7-4c91-8874-e2ef0e47f94f",
            "http://data.europa.eu/esco/skill/d908a818-70ef-419e-9d60-8f459114cb2f",
            "http://data.europa.eu/esco/skill/d38055cc-7c76-4e5e-90e7-5bb5d5851a92",
            "http://data.europa.eu/esco/skill/9e06bac6-6b91-48ca-8b7d-c1f48cdecd7c",
            "http://data.europa.eu/esco/skill/976455eb-5bbe-4493-ae64-ed16c3b009ea",
            "http://data.europa.eu/esco/skill/9dfab993-ecab-4292-9cb2-528a93eb3161",
            "http://data.europa.eu/esco/skill/fd33c66c-70c4-40e6-b87c-5495bd3bf26e",
            "http://data.europa.eu/esco/skill/52cf3037-ab53-4806-85ed-7fd21ea7f6a1",
            "http://data.europa.eu/esco/skill/7373abdb-ee5a-4949-b4ae-dee60ab37914",
            "http://data.europa.eu/esco/skill/fc72d86f-7e69-4bf8-b407-23303a798a0b",
            "http://data.europa.eu/esco/skill/b1272de4-1f5f-408b-8b26-061f2550fc72",
            "http://data.europa.eu/esco/skill/bac209e9-d337-4312-9041-17c2e75fa4df",
            "http://data.europa.eu/esco/skill/a59708e3-e654-4e37-8b8a-741c3b756eee",
            "http://data.europa.eu/esco/skill/9d2f77af-4307-4ac5-bc50-a9980cec7e83",
            "http://data.europa.eu/esco/skill/440d7494-3d79-4e1f-83ba-b346e116b471",
            "http://data.europa.eu/esco/skill/17702c40-e963-4593-847e-a332b1c9357e",
            "http://data.europa.eu/esco/skill/70a7b3b3-31ef-4b29-a30f-bb7299dff39b",
            "http://data.europa.eu/esco/skill/8ca08a35-2d2e-46f5-b440-8fc8ce736287",
            "http://data.europa.eu/esco/skill/84251cb1-babf-4d17-a3cd-baa642c24c8d",
            "http://data.europa.eu/esco/skill/3fb8b724-1d52-4d11-b8b8-1776f558d1f0",
            "http://data.europa.eu/esco/skill/ca73ac82-867a-4afa-9732-834aebe896ff",
            "http://data.europa.eu/esco/skill/3cee858e-79e8-4a4b-b99f-93c7f735d58b",
            "http://data.europa.eu/esco/skill/0737ad0a-75ac-4696-988b-565820577ac0",
            "http://data.europa.eu/esco/skill/4ed46658-cc30-4d37-9301-47dd354c92a2",
            "http://data.europa.eu/esco/skill/4e6d2538-a48e-48a7-8dad-14b067cfcb8b",
            "http://data.europa.eu/esco/skill/18ab25a5-ce2d-4cd5-b7b0-3890f595f77d",
            "http://data.europa.eu/esco/skill/31587950-8b69-4a15-91c2-74dae22d38a4",
            "http://data.europa.eu/esco/skill/ffa1d58b-ba09-49bd-a982-e743b2237006",
            "http://data.europa.eu/esco/skill/e6262391-ffda-4b8e-9f5c-89cf1a0e7384",
            "http://data.europa.eu/esco/skill/f683ae1d-cb7c-4aa1-b9fe-205e1bd23535",
            "http://data.europa.eu/esco/skill/89f6560b-2194-45c9-9ece-d33049a73eef",
            "http://data.europa.eu/esco/skill/4f5c1208-62fd-4e5a-a51e-306c06947e11",
            "http://data.europa.eu/esco/skill/ad008df3-dcaf-45ee-8892-2319a3b66c98",
            "http://data.europa.eu/esco/skill/f4a85869-1855-45d5-a43c-7ee8cd451996",
            "http://data.europa.eu/esco/skill/abdc7ac8-151f-40c6-bc1a-1e9b4b073290",
            "http://data.europa.eu/esco/skill/d3e9b00a-0891-4fa8-836d-8240ec3d2758",
            "http://data.europa.eu/esco/skill/f88002a8-6355-4d33-9496-285c166ff375",
            "http://data.europa.eu/esco/skill/ec0d2580-ee2f-45e6-a423-c69dd3f03868",
            "http://data.europa.eu/esco/skill/96eb286a-58b7-45ff-a916-5578d0b79b8c",
            "http://data.europa.eu/esco/skill/234aeb8d-56c3-4531-9193-1c5e6a8d16cb",
            "http://data.europa.eu/esco/skill/0d6714c6-720e-4985-9791-5ab85d28da79",
            "http://data.europa.eu/esco/skill/5cbfa47c-5363-46f2-91e7-01909e4f3b15",
            "http://data.europa.eu/esco/skill/6345d3a4-7d1a-40a9-9a82-6ed06beffe7b",
            "http://data.europa.eu/esco/skill/d5a59ca8-2e91-472e-8571-d12ce4478679",
            "http://data.europa.eu/esco/skill/8f849584-2326-4e7b-9a3e-d40d7cdb729f",
            "http://data.europa.eu/esco/skill/aba363e7-07ec-4eb2-a89e-bcba397c25ab",
            "http://data.europa.eu/esco/skill/751941a0-5645-4f8b-b97e-b252909d5edb",
            "http://data.europa.eu/esco/skill/bbbd263e-affb-4ec8-84fb-3e5d9e62306b",
            "http://data.europa.eu/esco/skill/ea99e1f2-ad03-4bb3-8960-1227fc76bb2a",
            "http://data.europa.eu/esco/skill/f47a1998-0beb-43be-9f46-380aa4d183da",
            "http://data.europa.eu/esco/skill/a57a54b6-2f2e-43e4-9621-b52f4a63cb08",
            "http://data.europa.eu/esco/skill/57231a22-4da7-49c8-97b8-75672feadf1e",
            "http://data.europa.eu/esco/skill/0a52bed5-fd29-45fb-aba6-e32bccfda1c1",
            "http://data.europa.eu/esco/skill/a2fef437-6719-47c2-8552-73b9fe81df86",
            "http://data.europa.eu/esco/skill/ed5bbc64-7017-4e2e-b44e-65df88013a84",
            "http://data.europa.eu/esco/skill/6f7512ab-d46d-402a-8c30-a8878d7c1f88",
            "http://data.europa.eu/esco/skill/51927003-a757-4552-804f-c045e21fdaf2",
            "http://data.europa.eu/esco/skill/378caeec-10a0-4162-b75c-ab374a42e93d",
            "http://data.europa.eu/esco/skill/6141595f-a016-4ef9-9487-3829f6804715",
            "http://data.europa.eu/esco/skill/de804504-bd5c-465d-b54f-9da299d8ee90",
            "http://data.europa.eu/esco/skill/f0393af3-bcd9-41af-a36e-0cfb83b60081",
            "http://data.europa.eu/esco/skill/64ab52f5-9200-45a3-a127-679b487c6415",
            "http://data.europa.eu/esco/skill/9973a5a2-7822-4161-99e9-95c781eb63f8",
            "http://data.europa.eu/esco/skill/47b9bbcf-356c-4782-83a4-7f5a1b2b51a3",
            "http://data.europa.eu/esco/skill/e8b89eb6-51e8-4c3a-babb-88b2e110376b",
            "http://data.europa.eu/esco/skill/d9eaf831-9348-4330-a83e-b7c099cdc8f6",
            "http://data.europa.eu/esco/skill/60d48be0-4260-4b2e-85b9-aedff7a9b7d9",
            "http://data.europa.eu/esco/skill/ccd0a1d9-afda-43d9-b901-96344886e14d",
            "http://data.europa.eu/esco/skill/d04ee340-5378-4601-8181-19da6d5cbfe0",
            "http://data.europa.eu/esco/skill/dc298fac-7ab4-4feb-9b43-33d947d7703e",
            "http://data.europa.eu/esco/skill/c2cbbccf-4ffd-4626-86e7-505b39c872a0",
            "http://data.europa.eu/esco/skill/b3f971b2-6c11-43d3-b5ce-8ba7f5a223c7",
            "http://data.europa.eu/esco/skill/9ba8fd27-275d-4bad-8c48-f668aa99a578",
            "http://data.europa.eu/esco/skill/c95c29fe-3971-46b7-80e9-1f7e75ba99b3",
            "http://data.europa.eu/esco/skill/64a3ce27-51c7-4b9d-8e6b-0b127558b3e2",
            "http://data.europa.eu/esco/skill/3a2d5b45-56e4-4f5a-a55a-4a4a65afdc43",
            "http://data.europa.eu/esco/skill/ec0bbf39-0edd-4015-a2c3-82ad563ce3a8",
            "http://data.europa.eu/esco/skill/f5b33464-4df9-4cf3-b468-b0a2d6b3ee85",
            "http://data.europa.eu/esco/skill/8764d4b1-e069-44f2-8baa-fb1eee1850ec",
            "http://data.europa.eu/esco/skill/4754d9fe-dc3a-4f8f-8ea6-5dbcea3a21ca",
            "http://data.europa.eu/esco/skill/a0b6f2b8-0f41-420d-9d7b-942026f83b50",
            "http://data.europa.eu/esco/skill/055ff233-6569-43db-9f95-a4b03dca97da",
            "http://data.europa.eu/esco/skill/8b94aa1e-89c9-459d-b3b4-1dfab8dec2df",
            "http://data.europa.eu/esco/skill/4ce06d6b-f3df-40b6-b950-570be5008032",
            "http://data.europa.eu/esco/skill/2daec0e6-f0c1-43e8-8178-aedba99130ec",
            "http://data.europa.eu/esco/skill/f4346299-e22a-4686-8e61-2c9b0cd51c7d",
            "http://data.europa.eu/esco/skill/06f33d6a-f7a7-4a2b-baa5-8c3c53bd1a9c",
            "http://data.europa.eu/esco/skill/9effa3d7-c0c4-4583-ad94-b496ba5e5f2c",
            "http://data.europa.eu/esco/skill/81633a44-f1db-4a01-a940-804c6905e330",
            "http://data.europa.eu/esco/skill/26ca7ed6-80a7-47e3-92f5-6e6016adeb95",
            "http://data.europa.eu/esco/skill/31d35d3d-2bf2-49aa-9c20-92566ef80277",
            "http://data.europa.eu/esco/skill/117373c8-f04b-4fff-8683-aebe36edac81",
            "http://data.europa.eu/esco/skill/2ddb1226-1117-4eb4-ae65-3a2f5a5a3c80",
            "http://data.europa.eu/esco/skill/1b7c716e-af95-4cda-a42f-6479c8b139f5",
            "http://data.europa.eu/esco/skill/b928ae44-b19f-49c7-a823-b09094de1063",
            "http://data.europa.eu/esco/skill/33d49d4f-31ec-473f-9b8a-b555aa5116bb",
            "http://data.europa.eu/esco/skill/acf0a78c-f4e3-4bd9-a5cc-9cf4791b3b95",
            "http://data.europa.eu/esco/skill/6e34b68e-0d61-49f2-bc4d-571c0326d857",
            "http://data.europa.eu/esco/skill/ed1d8bd4-cd2a-4c64-b665-56d70651026c",
            "http://data.europa.eu/esco/skill/906ef09c-959c-42de-8c65-6e11c73c3570",
            "http://data.europa.eu/esco/skill/8fa840f7-e66a-4370-8e6d-7d554260c31c",
            "http://data.europa.eu/esco/skill/84f73180-4a45-49f4-9140-cb49bbc29531",
            "http://data.europa.eu/esco/skill/9136cbf1-7916-4f1c-bc9a-0318ee1d6016",
            "http://data.europa.eu/esco/skill/085bfce5-70fe-43da-9499-307725e89bbc",
            "http://data.europa.eu/esco/skill/251b5528-693c-4ef9-a7f6-9924bb8d7ef8",
            "http://data.europa.eu/esco/skill/73c7d0d9-092f-4f6e-800a-5759f45a6fc6",
            "http://data.europa.eu/esco/skill/0cc02ab5-da14-4fd4-963d-192fe71d3225",
            "http://data.europa.eu/esco/skill/688e1fc3-cb58-4d56-85a0-97b84f37fb4a",
            "http://data.europa.eu/esco/skill/e0ae0101-ab8f-47a2-938b-ab0cc367b3b5",
            "http://data.europa.eu/esco/skill/054f26ab-cfdc-4077-883c-1c4c9e9b310a",
            "http://data.europa.eu/esco/skill/3413584b-a4e9-4f8d-b836-c9bfee0ed80b",
            "http://data.europa.eu/esco/skill/df538078-0ccb-4125-a61d-ebaa648b39f2",
            "http://data.europa.eu/esco/skill/8645704c-a896-4ac1-8189-9ac5586d5c3a",
            "http://data.europa.eu/esco/skill/c5422318-3218-4ce9-9bdd-f38b93ff63a3",
            "http://data.europa.eu/esco/skill/6b30644d-bdc5-4bca-b1af-edbf5c6b15ee",
            "http://data.europa.eu/esco/skill/7dfbe0f3-076f-4a19-9eaa-db4d6db704b6",
            "http://data.europa.eu/esco/skill/632843be-2872-47b4-a92e-352629f9bd88",
            "http://data.europa.eu/esco/skill/cfa2be0d-96d5-4017-a866-962efb9c5070",
            "http://data.europa.eu/esco/skill/28a0fe16-4e03-4d5e-8f0e-5198309e77a9",
            "http://data.europa.eu/esco/skill/7e796b51-49d7-4e73-95af-2e7323763f15",
            "http://data.europa.eu/esco/skill/465e7c78-cd26-4503-a5ca-40009e807705",
            "http://data.europa.eu/esco/skill/c453cf81-6197-428e-84c6-70c773b63f27",
            "http://data.europa.eu/esco/skill/b3a75400-103f-42c7-b742-be11d161d05b",
            "http://data.europa.eu/esco/skill/b0a3bb25-a02f-43ed-ab1f-994fa66a424a",
            "http://data.europa.eu/esco/skill/438ff259-d086-4004-acda-1ec3e1631bc6",
            "http://data.europa.eu/esco/skill/a9622bbb-3a6f-4819-a3fe-cd6a447a78e9",
            "http://data.europa.eu/esco/skill/cb886ee9-c4e4-4d3b-9961-228e9f283a69",
            "http://data.europa.eu/esco/skill/f28617ad-afdd-4041-814c-216153a38998",
            "http://data.europa.eu/esco/skill/df329d70-87b0-466b-a214-3431f36e2a5e",
            "http://data.europa.eu/esco/skill/4ffc3a4c-b4f3-4082-b030-93799725d01b",
            "http://data.europa.eu/esco/skill/44b1a100-7240-4943-9133-d8c6d1f0b50a",
            "http://data.europa.eu/esco/skill/5fd6c193-93c6-4f2d-bf9b-e0debb851f2d",
            "http://data.europa.eu/esco/skill/0e78d9c9-7c3c-4c5c-82cb-205c16edbcb8",
            "http://data.europa.eu/esco/skill/5e14de3d-d055-427e-9f5b-eb068f56b019",
            "http://data.europa.eu/esco/skill/bad66a63-4e3f-41f9-9e5d-a4d5f12f8d36",
            "http://data.europa.eu/esco/skill/5c8aa481-9e7c-4f32-8197-a8b0fd7ca186",
            "http://data.europa.eu/esco/skill/ba04b19b-0b18-4d5c-b799-4db31a98e58d",
            "http://data.europa.eu/esco/skill/12b7d5a1-1ad4-45dc-b0fa-b31d1eb2c79a",
            "http://data.europa.eu/esco/skill/f5f9b6fe-0ff2-4c0b-887a-12702d8612ba",
            "http://data.europa.eu/esco/skill/604f62cc-0f04-4903-b9f9-7128f72d4e5a",
            "http://data.europa.eu/esco/skill/67dbfc2a-3dd4-41a1-90bd-97035e7add60",
            "http://data.europa.eu/esco/skill/fc5818b4-66f9-498b-a1bc-ff651db2af68",
            "http://data.europa.eu/esco/skill/8bc3c77c-30c3-45e6-bd61-069ba433ecfe",
            "http://data.europa.eu/esco/skill/fcbb3a90-cdde-49ff-9ecc-8e2c0a6dfca2",
            "http://data.europa.eu/esco/skill/e743a3f3-ba4a-4dab-99a6-6321dba6b502",
            "http://data.europa.eu/esco/skill/49fed129-e32b-4f67-b80a-609d79e45b20",
            "http://data.europa.eu/esco/skill/49320ec7-65f9-4f90-bed2-2d41784f1836",
            "http://data.europa.eu/esco/skill/eeef9d73-f215-40fb-92cc-6498f13fc3f2",
            "http://data.europa.eu/esco/skill/13bdd41a-2a18-441f-96db-41252c519413",
            "http://data.europa.eu/esco/skill/126da6db-593e-4dd2-b35d-86f599a622f6",
            "http://data.europa.eu/esco/skill/f1a56786-e6d7-4b2e-a39c-23e09024d473",
            "http://data.europa.eu/esco/skill/b2bdd9ad-4ecb-4ced-80c3-623686ef5328",
            "http://data.europa.eu/esco/skill/67a118f4-8a5f-48f7-8e5f-de34b9ca2c37",
            "http://data.europa.eu/esco/skill/2b7a79e5-84d8-4880-be66-3d9bb05bea17",
            "http://data.europa.eu/esco/skill/cb5cccc9-abe4-4b11-abe6-d27e5cd85fb1",
            "http://data.europa.eu/esco/skill/e819cc1e-09d9-47f2-b418-93972852daef",
            "http://data.europa.eu/esco/skill/fb82ae25-b59f-43fd-aacf-a8308afbe85e",
            "http://data.europa.eu/esco/skill/43ec6db6-33f7-449b-a566-5779a7e8997a",
            "http://data.europa.eu/esco/skill/5da42cfd-1da8-4e4f-b68e-4f821d005fc5",
            "http://data.europa.eu/esco/skill/a571ae14-3e16-4fd3-a615-5646e0b0b696",
            "http://data.europa.eu/esco/skill/9bfae677-2b7f-49bf-b7eb-2ade795df734",
            "http://data.europa.eu/esco/skill/b63ba4e4-ebf4-4e17-b0d6-662244022a4f",
            "http://data.europa.eu/esco/skill/7f0b1a66-ebaf-4c8a-b606-4c1844eb692e",
            "http://data.europa.eu/esco/skill/6b3afb82-c4ea-4f14-be54-a757fb762663",
            "http://data.europa.eu/esco/skill/9710092c-f174-4e84-95bf-2064d39ad7a0",
            "http://data.europa.eu/esco/skill/557e9247-1a2d-4497-aa7f-0cded7768da1",
            "http://data.europa.eu/esco/skill/d70701ca-d3be-44cb-963e-8e7142555144",
            "http://data.europa.eu/esco/skill/3e2ab38a-4519-4bef-b762-a8bf4836a775",
            "http://data.europa.eu/esco/skill/9a35cb50-8106-45f9-8aba-2df99dd6f913",
            "http://data.europa.eu/esco/skill/b4877620-c375-45f3-a678-2ddb4dae6a29",
            "http://data.europa.eu/esco/skill/2180bd8c-86de-4889-8165-adac902eee9d",
            "http://data.europa.eu/esco/skill/3c7d58c9-125e-4d8d-9102-b8022c54d0b5",
            "http://data.europa.eu/esco/skill/f39ea734-faef-48a7-9544-14e691aed6ab",
            "http://data.europa.eu/esco/skill/d560df46-dda4-4407-bab2-b87353a96814",
            "http://data.europa.eu/esco/skill/dba46f87-0831-49cd-a1c7-340a653c0221",
            "http://data.europa.eu/esco/skill/382c11ed-20d5-4ae7-b60e-15fec527fa6c",
            "http://data.europa.eu/esco/skill/1605025e-a179-421f-8f35-5b07d182a6b2",
            "http://data.europa.eu/esco/skill/16a00c69-9c74-4c37-96d7-6301d285e5ce",
            "http://data.europa.eu/esco/skill/2522a6ce-3202-4ac8-9f5b-b9cb5a3a83a1",
            "http://data.europa.eu/esco/skill/b5c7e0dc-dda3-4790-a7d1-d6d8023b3089",
            "http://data.europa.eu/esco/skill/e842ed4c-23c1-4c29-b1b1-b490889db735",
            "http://data.europa.eu/esco/skill/5bac4bcc-1846-4688-ba02-ad2b51544282",
            "http://data.europa.eu/esco/skill/861313ba-3a1a-4d33-842a-1b8e45e2415b",
            "http://data.europa.eu/esco/skill/4cb58ef4-67a9-4bea-a743-2c83ebd60e79",
            "http://data.europa.eu/esco/skill/c8fa4313-80b0-4f37-8b1b-1739707bc362",
            "http://data.europa.eu/esco/skill/54ddd422-5905-44db-93d4-03b17b913c5e",
            "http://data.europa.eu/esco/skill/47ca0da1-cae5-4395-ae6b-fd97b9ff48d3",
            "http://data.europa.eu/esco/skill/47a49cd6-097d-457a-9f7b-c290c14930d5",
            "http://data.europa.eu/esco/skill/be0ff6d8-5e61-4021-9c89-e40cf0b75cc6",
            "http://data.europa.eu/esco/skill/9c6e951f-aa88-4890-983d-e7a6ee49f5bb",
            "http://data.europa.eu/esco/skill/c2296e2e-5fef-4f24-a24f-e5b3792268be",
            "http://data.europa.eu/esco/skill/f3d8547b-e7c4-4024-8f2d-0ccf3f522779",
            "http://data.europa.eu/esco/skill/97c1895a-a890-47da-abfc-79dc272add2e",
            "http://data.europa.eu/esco/skill/ee02393d-27dd-438a-8879-b8088ce45e79",
            "http://data.europa.eu/esco/skill/d90bad2f-7fcc-4e85-a02f-ba4837732892",
            "http://data.europa.eu/esco/skill/3c748b49-fe85-42f6-b62d-b877bc8edb29",
            "http://data.europa.eu/esco/skill/f22dd07b-cb0f-4b18-ba54-d922dfaa8c14",
            "http://data.europa.eu/esco/skill/83934f53-5ffa-478f-9f8f-b779e9b4fc7a",
            "http://data.europa.eu/esco/skill/11430d93-c835-48ed-8e70-285fa69c9ae6",
            "http://data.europa.eu/esco/skill/2c4e11ef-da18-4e19-816b-e6bc19e12424",
            "http://data.europa.eu/esco/skill/db77825e-0f3e-47d0-abdb-356794484272",
            "http://data.europa.eu/esco/skill/2f04bb71-d476-43b3-a60b-5df1d05f5681",
            "http://data.europa.eu/esco/skill/fe4a9fb8-2963-4b23-8a18-e033197304f2",
            "http://data.europa.eu/esco/skill/b1604a2d-c26a-427d-bda9-b8073a319fe9",
            "http://data.europa.eu/esco/skill/78a74fd1-efc6-49dc-adb1-1b0fbe35c22c",
            "http://data.europa.eu/esco/skill/bf7ea00f-db77-4a95-9b9f-2d12b6d9fcb4",
            "http://data.europa.eu/esco/skill/0b449eb1-d082-4e62-95d7-5cf59bdf2097",
            "http://data.europa.eu/esco/skill/9983816d-cc78-4d3f-9e3c-c7baa9ebc77a",
            "http://data.europa.eu/esco/skill/c6c23b4b-c9c9-42c8-86d2-3646631bdbd4",
            "http://data.europa.eu/esco/skill/b21e2984-c6b2-4352-bbe8-8f3b03bd50f6",
            "http://data.europa.eu/esco/skill/0ced619a-0f59-4802-88ea-e97bf1b57f95",
            "http://data.europa.eu/esco/skill/e8bae3fe-6d8d-457e-a20b-6f8eea4d5402",
            "http://data.europa.eu/esco/skill/5ce80f97-b385-4a41-9339-a6593d0709c1",
            "http://data.europa.eu/esco/skill/7c821387-b428-4f75-b5a7-7b038aa81cfd",
            "http://data.europa.eu/esco/skill/5c48050b-c4ca-467e-a4d3-9547bf362a17",
            "http://data.europa.eu/esco/skill/42fcf80d-7b45-40f3-bd2f-5411c4c0dfa0",
            "http://data.europa.eu/esco/skill/6430eb0d-c6de-46b7-a7fc-3dd2e8e62a69",
            "http://data.europa.eu/esco/skill/219dd03d-4168-416a-be40-f4becca55130",
            "http://data.europa.eu/esco/skill/cf3e976d-2c3e-495c-a874-f9e8a66d3b48",
            "http://data.europa.eu/esco/skill/6f728d63-5c45-40b3-8bfd-e17c5dba28ad",
            "http://data.europa.eu/esco/skill/1ce4cddd-4a74-458e-a2d4-152ca939475a",
            "http://data.europa.eu/esco/skill/7adb8473-bcc6-4ff8-9850-76d945d40092",
            "http://data.europa.eu/esco/skill/edf081e4-d2dc-4a60-8c49-7b7e45b5ebe3",
            "http://data.europa.eu/esco/skill/94758266-bb0f-4e41-bedb-349ecacfaf5c",
            "http://data.europa.eu/esco/skill/68174d5f-1646-42e2-b765-6a66cd1e59ae",
            "http://data.europa.eu/esco/skill/08236fee-c405-4f30-8858-f10d7d5c94b7",
            "http://data.europa.eu/esco/skill/f9670490-8aa4-4540-b121-d440a8294aab",
            "http://data.europa.eu/esco/skill/0868eef3-2213-4572-9343-74931345a7d3",
            "http://data.europa.eu/esco/skill/3ec2e4d6-7000-4905-bf1a-c5b1679416de",
            "http://data.europa.eu/esco/skill/2b24cbb7-f94e-43c1-8377-138f80ad5c19",
            "http://data.europa.eu/esco/skill/348b74cd-49ce-4844-8bdf-ec188b497213",
            "http://data.europa.eu/esco/skill/d3286405-49f8-4e8a-8046-a4376b4e7963",
            "http://data.europa.eu/esco/skill/58c3ed60-c06b-47c0-b213-4e3608608644",
            "http://data.europa.eu/esco/skill/32a2c63d-2d13-4784-abb8-678ef2cd8a46",
            "http://data.europa.eu/esco/skill/3d03c40c-1b83-4793-8902-2523f40af59e",
            "http://data.europa.eu/esco/skill/bf1f763e-c505-46e7-8af3-e48f13ec5f44",
            "http://data.europa.eu/esco/skill/a4e31589-3632-4926-91d0-15b889c90b9b",
            "http://data.europa.eu/esco/skill/50597e96-9b6a-4736-ac79-dd80f36c0269",
            "http://data.europa.eu/esco/skill/d0a7144b-ba91-4489-acb8-a055c6b7e968",
            "http://data.europa.eu/esco/skill/5ef0c719-5bcb-49f8-b8eb-824388225333",
            "http://data.europa.eu/esco/skill/8203b07a-8097-4383-aff6-603b7df7f0f4",
            "http://data.europa.eu/esco/skill/5f5e9350-1d13-4391-b9e1-07f6b2047fc5",
            "http://data.europa.eu/esco/skill/72812eb6-5a0f-48e8-815d-3d2a16b8e963",
            "http://data.europa.eu/esco/skill/b13e0689-636d-42d0-86c3-df9d6f85a882",
            "http://data.europa.eu/esco/skill/7d5ff73e-7bea-436d-974f-3a51ce24a138",
            "http://data.europa.eu/esco/skill/e08821b5-d6dd-4db9-a0f1-43185653c244",
            "http://data.europa.eu/esco/skill/8afe68ff-b261-4fa8-ab25-bb30b0e5c292",
            "http://data.europa.eu/esco/skill/ef0527db-0442-4f05-a150-533b410e1cc1",
            "http://data.europa.eu/esco/skill/69f0167e-75d9-46cf-847f-72a592880ebb",
            "http://data.europa.eu/esco/skill/dd276faa-ef0a-4ad0-8744-37b883a79b64",
            "http://data.europa.eu/esco/skill/08b04e53-ed25-41a2-9f90-0b9cd939ba3d",
            "http://data.europa.eu/esco/skill/e06ec50c-68f9-470f-8dc2-7488109c2dfd",
            "http://data.europa.eu/esco/skill/683bc3e3-6ec1-4ad2-8d62-d0ac1fabbe76",
            "http://data.europa.eu/esco/skill/4fea6307-bdc0-423d-ad63-c61755c14522",
            "http://data.europa.eu/esco/skill/b5b09dac-5b28-40a5-b671-5f9aa31f65d8",
            "http://data.europa.eu/esco/skill/aff205d0-b338-4020-9c19-45044b545835",
            "http://data.europa.eu/esco/skill/9ce52938-fe28-4548-b38d-0c783933e19a",
            "http://data.europa.eu/esco/skill/367c240d-5f34-423a-86b1-866ad27206ea",
            "http://data.europa.eu/esco/skill/7a950986-27fe-4b6c-adf3-88f211e77019",
            "http://data.europa.eu/esco/skill/300d432b-f457-4cd3-9cb1-1858c7d14954",
            "http://data.europa.eu/esco/skill/ef64005d-75bc-4597-8ed6-717edec5e55e",
            "http://data.europa.eu/esco/skill/6195c5f7-a4fb-425d-a3dd-c4467c4471a3",
            "http://data.europa.eu/esco/skill/509909a2-4a8f-4ead-8ad0-968df63b77cc",
            "http://data.europa.eu/esco/skill/628664ec-a63b-4486-8d65-02aba81c82a0",
            "http://data.europa.eu/esco/skill/6ad40ca3-dc34-4442-b23f-0b1b28936f56",
            "http://data.europa.eu/esco/skill/e36c1f47-3913-4d35-a6ae-fd9a7243483f",
            "http://data.europa.eu/esco/skill/418a56a2-7cfc-4b6d-8cf5-5c2add99a849",
            "http://data.europa.eu/esco/skill/5c66ce3a-658d-43b1-9d8d-0f39be6c0f2e",
            "http://data.europa.eu/esco/skill/545c80e8-7ab2-42fe-af64-242211e94709",
            "http://data.europa.eu/esco/skill/df0612db-366c-4ff5-b106-40b9e385195c",
            "http://data.europa.eu/esco/skill/7234e847-451d-499a-9c7b-0050a4e758d6",
            "http://data.europa.eu/esco/skill/f6868852-9bf6-4899-a5d6-5f9532fb877d",
            "http://data.europa.eu/esco/skill/ddb65832-91a7-4157-9488-719a0e2ad87b",
            "http://data.europa.eu/esco/skill/3be79ccc-a455-49b9-8c65-55c50071ba5b",
            "http://data.europa.eu/esco/skill/598de5b0-5b58-4ea7-8058-a4bc4d18c742",
            "http://data.europa.eu/esco/skill/224423a6-ac6a-4b3e-be7c-e38b41f82116",
            "http://data.europa.eu/esco/skill/d503a629-9ac7-475f-8fd9-4cdcc14711cd",
            "http://data.europa.eu/esco/skill/8dcc1c68-1ebe-412b-b437-cab3c26da509",
            "http://data.europa.eu/esco/skill/cd56a093-3400-4635-80bd-b9611dcf1542",
            "http://data.europa.eu/esco/skill/c900bdb8-0ed1-4d39-80f8-6e0a05a575f4",
            "http://data.europa.eu/esco/skill/e5d1f825-60ed-4bdd-872a-e748c387f777",
            "http://data.europa.eu/esco/skill/61c81161-b732-4c47-aba5-9e554d9a853f",
            "http://data.europa.eu/esco/skill/3d427a49-4f85-42cd-8a92-62d4a34f41a1",
            "http://data.europa.eu/esco/skill/c0e2f74c-51f0-4020-9756-d190848cf181",
            "http://data.europa.eu/esco/skill/c2ce7fe9-203b-492a-961e-9698dce7de19",
            "http://data.europa.eu/esco/skill/d5bcc417-a004-4165-b7f1-ab739eadd2f4",
            "http://data.europa.eu/esco/skill/ce8ae6ca-61d8-4174-b457-641de96cbff4",
            "http://data.europa.eu/esco/skill/6b42f6d0-709f-4011-b777-2c8ecc559214",
            "http://data.europa.eu/esco/skill/ce26e71f-2d47-474e-89b6-7920931ac4fc",
            "http://data.europa.eu/esco/skill/dd0e44b9-b285-4563-8ffc-1d4d96bf548c",
            "http://data.europa.eu/esco/skill/d81b58ba-52e4-45fa-9f7c-f59b87fa6041",
            "http://data.europa.eu/esco/skill/f4a6e9f7-5cff-46c0-894c-59c20bb78694",
            "http://data.europa.eu/esco/skill/b04c4965-4fd7-4dfa-a8d4-0cec5c5f5d0c",
            "http://data.europa.eu/esco/skill/69bbd53f-fbb0-4476-b4b2-ef7844464e28",
            "http://data.europa.eu/esco/skill/93aaf65c-5aad-4e6f-a7ba-ce9bf7c0e6e6",
            "http://data.europa.eu/esco/skill/4095c6dd-3b00-44d1-9308-878e08737e92",
            "http://data.europa.eu/esco/skill/e2fb1ee4-fbf9-4690-b677-3e6ff7cfdc48",
            "http://data.europa.eu/esco/skill/28b7d7fb-0483-4877-9aaa-f990f10f16f5",
            "http://data.europa.eu/esco/skill/6290031b-4183-4849-ab02-11a3ea0494da",
            "http://data.europa.eu/esco/skill/a4c2aa71-086f-4a46-b481-2619673c9dfe",
            "http://data.europa.eu/esco/skill/c216b67a-7462-4f6a-ba14-db02f6e91b68",
            "http://data.europa.eu/esco/skill/30d926ba-1615-417c-a799-e24a3ea239b9",
            "http://data.europa.eu/esco/skill/33841903-78d8-4273-950d-755248290c2d",
            "http://data.europa.eu/esco/skill/9ba355be-f119-42b6-a7d6-52f6ce2e4a3f",
            "http://data.europa.eu/esco/skill/be59c6bd-0e88-4ffe-bc81-50b86be815e6",
            "http://data.europa.eu/esco/skill/ee3b1553-2dc5-434b-8dcf-4ae3a40b3a48",
            "http://data.europa.eu/esco/skill/e63da991-d405-4463-9874-3555c80082d9",
            "http://data.europa.eu/esco/skill/5af49836-2269-46d9-87b9-0d3140c6c22e",
            "http://data.europa.eu/esco/skill/fda917eb-0d6a-4371-8c93-2a10bf950966",
            "http://data.europa.eu/esco/skill/7a757fa5-9a6f-43ab-9e66-f8f4dba1ffcb",
            "http://data.europa.eu/esco/skill/d96078b0-12f4-47d3-a260-61d22050a840",
            "http://data.europa.eu/esco/skill/5cf70b66-f1b4-4c50-94fb-15ec74fad028",
            "http://data.europa.eu/esco/skill/ed39edd3-44d0-4a16-9796-bfb99f92baf3",
            "http://data.europa.eu/esco/skill/fa3e5cec-703e-4d46-b3bb-e8a89540ab54",
            "http://data.europa.eu/esco/skill/e439742d-558d-4f52-885e-afdc740b48cf",
            "http://data.europa.eu/esco/skill/778ca7ad-6c5d-4868-9b96-a7777dca799c",
            "http://data.europa.eu/esco/skill/8327754d-3990-4056-b07e-4faa49cfcd26",
            "http://data.europa.eu/esco/skill/25df422a-bf0a-4f76-8631-d0be91cb8751",
            "http://data.europa.eu/esco/skill/c8d53d69-d900-4092-af80-c5d0422bcdb3",
            "http://data.europa.eu/esco/skill/0a30e5a8-60a8-4fda-9f89-8e9cee38da5a",
            "http://data.europa.eu/esco/skill/97fb21db-a4e5-4b03-af21-03b9d510d872",
            "http://data.europa.eu/esco/skill/a7a14be2-78b5-4b4a-ba77-2658285e2756",
            "http://data.europa.eu/esco/skill/c4b1f326-224a-420a-b8b3-814a8f13b6cb",
            "http://data.europa.eu/esco/skill/574032f3-e427-49ad-8d88-2341ac6e1a7d",
            "http://data.europa.eu/esco/skill/2857540c-180b-4208-9127-e94a01871966",
            "http://data.europa.eu/esco/skill/bee296bc-48f7-4df6-bd2c-8616c8a6c8de",
            "http://data.europa.eu/esco/skill/bdb4f02e-72d5-41ca-a662-0498dc3fb070",
            "http://data.europa.eu/esco/skill/0458f6a0-cb54-4ff5-a543-b2c5354e17c0",
            "http://data.europa.eu/esco/skill/a3ce2bf7-5562-4639-9b52-fdb4850a3904",
            "http://data.europa.eu/esco/skill/78b26c92-da70-4c72-9f10-7a8c8b9db02e",
            "http://data.europa.eu/esco/skill/1177a1e9-213a-477b-b57c-364368c68b95",
            "http://data.europa.eu/esco/skill/f399a9c1-be41-4117-bf86-a64b503c4cfd",
            "http://data.europa.eu/esco/skill/41be77d3-d683-4221-a0e4-fff732212402",
            "http://data.europa.eu/esco/skill/b8b2a035-38f0-4c0e-94ee-edefc266c643",
            "http://data.europa.eu/esco/skill/25f0ea33-b4a2-4f31-b7b4-7d20e827b180",
            "http://data.europa.eu/esco/skill/3703eac1-8941-4d14-b305-b03f653b08c8",
            "http://data.europa.eu/esco/skill/e79dab00-fe87-48f2-b8cc-f48b68bc7f35",
            "http://data.europa.eu/esco/skill/9035afc4-5317-455d-ab60-f32e2ecc6bfc",
            "http://data.europa.eu/esco/skill/85038d57-1fdd-40de-b908-2c005545a8c3",
            "http://data.europa.eu/esco/skill/18eeffee-2879-42e0-b5fe-5739c6883a01",
            "http://data.europa.eu/esco/skill/ffdd5e53-7191-4043-ae0e-eea1bb077080",
            "http://data.europa.eu/esco/skill/d87fe0be-9055-4a76-a444-e98b5bfad9d8",
            "http://data.europa.eu/esco/skill/6cad4013-3055-47ba-8316-0720e1a55256",
            "http://data.europa.eu/esco/skill/3d957fb2-b8f4-4b4c-b955-23299c761ad5",
            "http://data.europa.eu/esco/skill/0c9f20cb-32fb-4ff7-877e-508762791151",
            "http://data.europa.eu/esco/skill/94dd823c-148e-4614-a6e8-99249b16357d",
            "http://data.europa.eu/esco/skill/79a5bc25-5b59-47bc-a4c2-fb8e8cb1ddf6",
            "http://data.europa.eu/esco/skill/52c0b1a2-a57f-488b-b09d-19af929412aa",
            "http://data.europa.eu/esco/skill/118a9b27-1c5a-4214-b685-1a4d9e6365ea",
            "http://data.europa.eu/esco/skill/bb562019-5222-45db-a16c-730edbb1d27c",
            "http://data.europa.eu/esco/skill/8d17898e-cd9c-444d-b084-8708a5447747",
            "http://data.europa.eu/esco/skill/be457a1c-5973-4781-9bb8-809703931f43",
            "http://data.europa.eu/esco/skill/ab20726a-14dd-4536-9aa1-6166bc1a5832",
            "http://data.europa.eu/esco/skill/9ce670ec-318c-4a91-9179-03437db728dc",
            "http://data.europa.eu/esco/skill/b769f524-0f25-44d7-8538-5c46710a040e",
            "http://data.europa.eu/esco/skill/206f5738-e91f-4390-84a2-fcb864bc11b1",
            "http://data.europa.eu/esco/skill/56a7f561-1d55-43c9-9cd7-36a0a9bc6c50",
            "http://data.europa.eu/esco/skill/99207709-d076-4cce-ba38-e90d3bb28806",
            "http://data.europa.eu/esco/skill/216e5ea6-c683-4945-9f96-03483dc56d9f",
            "http://data.europa.eu/esco/skill/2b834a98-14ff-47e1-8aef-cf8ffae912fa",
            "http://data.europa.eu/esco/skill/dcdd5ddf-82a6-4ac3-8edc-d4b23cae9a88",
            "http://data.europa.eu/esco/skill/33b55d3a-81f5-45cb-af40-06cd54db78e1",
            "http://data.europa.eu/esco/skill/34bc01ed-dd78-49c7-bcfb-c17bb66688b5",
            "http://data.europa.eu/esco/skill/1bba98a7-92b9-450b-9235-e0c905f8f3c4",
            "http://data.europa.eu/esco/skill/f7e2eb04-3e50-4561-bce1-7e51a1fec308",
            "http://data.europa.eu/esco/skill/713fb616-118e-40bc-9366-4a69879a49d5",
            "http://data.europa.eu/esco/skill/75f839c2-fd9a-4ad3-8921-6e734608568d",
            "http://data.europa.eu/esco/skill/867137fb-ff1b-4ca3-99f3-cb6969aa2c68",
            "http://data.europa.eu/esco/skill/736ef286-fbd3-4e5c-a4b4-d1e2008c9898",
            "http://data.europa.eu/esco/skill/5d3798b0-d3a3-45ef-9df2-8b8756d4d79c",
            "http://data.europa.eu/esco/skill/8e91c9b3-45de-4db2-91df-138d10031cf0",
            "http://data.europa.eu/esco/skill/d2adb565-a865-424d-aeb5-9fbcbf2afe2a",
            "http://data.europa.eu/esco/skill/9ab0a456-e4f6-4070-a360-46e1009b6c0d",
            "http://data.europa.eu/esco/skill/4d97e3c3-f335-47cc-a4ee-0d779fd42222",
            "http://data.europa.eu/esco/skill/bf6c5ed4-84af-440f-abcc-7fa5ba19c738",
            "http://data.europa.eu/esco/skill/14832d87-2f2f-4895-b290-e4760ebae42a",
            "http://data.europa.eu/esco/skill/8a7a0d46-d88a-4ab0-bfa4-d861a82bab3c",
            "http://data.europa.eu/esco/skill/553e1d2d-abf1-4b37-8ac4-c5c78a8edbc0",
            "http://data.europa.eu/esco/skill/88b9ac3d-1c1d-44d1-9584-c4f5dd2503b3",
            "http://data.europa.eu/esco/skill/53403f06-6c51-4a24-9491-d8a6200526ce",
            "http://data.europa.eu/esco/skill/14525f24-afb4-4a4e-80a7-facf27080cdd",
            "http://data.europa.eu/esco/skill/d3e36164-79aa-4f14-9a8c-d88d38f84ea0",
            "http://data.europa.eu/esco/skill/8759c352-8b00-498f-985a-935f13e1b043",
            "http://data.europa.eu/esco/skill/a4a882a1-0263-4dd2-b29a-f0028cac2393",
            "http://data.europa.eu/esco/skill/41ec47dd-08b3-464a-9c45-c706f3e74467",
            "http://data.europa.eu/esco/skill/27ad6528-0914-4049-87c0-2f05791d0e2e",
            "http://data.europa.eu/esco/skill/fdf15b35-9028-4acb-bfd2-43f00a015cd9",
            "http://data.europa.eu/esco/skill/ae57a9c3-f761-4b03-be2b-99a781704c73",
            "http://data.europa.eu/esco/skill/af313ba1-a39e-49ac-99ec-94630fbe4f7f",
            "http://data.europa.eu/esco/skill/ddc3119d-1d6e-4324-9125-a3380d299ac5",
            "http://data.europa.eu/esco/skill/f049d050-12da-4e40-813a-2b5eb6df6b51",
            "http://data.europa.eu/esco/skill/85a67bc3-708c-4eab-86f6-4529d01d12a6",
            "http://data.europa.eu/esco/skill/b0ac313d-de44-4344-ada6-76d2177b6d92",
            "http://data.europa.eu/esco/skill/90be83d9-56f1-46a4-a6bc-96c51f6c9025",
            "http://data.europa.eu/esco/skill/7b0d5000-00da-4864-b776-6de49a87a669",
            "http://data.europa.eu/esco/skill/0f365cf9-4f68-4a25-affc-75de20bcdc09",
            "http://data.europa.eu/esco/skill/a56dec32-05ef-45de-a256-ee6741f356db",
            "http://data.europa.eu/esco/skill/b8271c8a-cba6-4baa-80f5-6361af3f07e3",
            "http://data.europa.eu/esco/skill/417eb6b1-9e5b-4b3a-bfd0-85132c9f648d",
            "http://data.europa.eu/esco/skill/0189f448-179e-47cc-9716-c5c3ac4b1aec",
            "http://data.europa.eu/esco/skill/861cc783-6d4c-442e-9081-3a6e389be454",
            "http://data.europa.eu/esco/skill/64e09849-a7db-4d6b-a932-66264420eb97",
            "http://data.europa.eu/esco/skill/fc2f1d4f-a46f-471e-a618-cbd4d0496a53",
            "http://data.europa.eu/esco/skill/e74f4f21-8d45-412c-b5c3-652e6167ee32",
            "http://data.europa.eu/esco/skill/55cd5608-1497-4ed1-96d0-cb9494749a09",
            "http://data.europa.eu/esco/skill/0daaf096-1639-461c-bf1a-bbeea77e6b67",
            "http://data.europa.eu/esco/skill/750f86d2-18be-4015-84b1-d4a26d9b2d88",
            "http://data.europa.eu/esco/skill/bcc0db5f-c1e8-4f08-b68b-020396ef07b9",
            "http://data.europa.eu/esco/skill/37a438f3-e28c-4e32-83c5-299f047c1dc9",
            "http://data.europa.eu/esco/skill/d6b31d8d-2f47-425a-a2b2-a6dddb3d2b19",
            "http://data.europa.eu/esco/skill/b363bb5f-2c79-40af-94da-33e06f9dee9f",
            "http://data.europa.eu/esco/skill/77592603-505b-4096-97dd-d0def6bd7601",
            "http://data.europa.eu/esco/skill/e6b8f934-7f35-4dc6-9a64-f6c43acdc00b",
            "http://data.europa.eu/esco/skill/8f113113-aa20-48f7-8da1-ac0051505d03",
            "http://data.europa.eu/esco/skill/bec4359e-cb92-468f-a997-8fb28e32fba9",
            "http://data.europa.eu/esco/skill/d68e8a54-7456-4f13-98d3-7aa0787fc6e2",
            "http://data.europa.eu/esco/skill/8682024b-2c8b-4de4-8cd7-23ea6169da57",
            "http://data.europa.eu/esco/skill/9ecd4016-b9de-480d-8ec3-9881644f0eda",
            "http://data.europa.eu/esco/skill/5fed696b-77d6-46e1-aa57-e520928a9837",
            "http://data.europa.eu/esco/skill/5706691c-2c38-46e4-9e88-c55be7e5d3ff",
            "http://data.europa.eu/esco/skill/1019423b-3368-4f83-b24f-19e5fa23e816",
            "http://data.europa.eu/esco/skill/25b291b5-8245-4d9d-b391-86a8a31d7109",
            "http://data.europa.eu/esco/skill/ea412de3-9d7a-4943-8d8c-c6d3c7495d0e",
            "http://data.europa.eu/esco/skill/56a10e3e-c9e7-4d39-b6f1-6013398fcb3a",
            "http://data.europa.eu/esco/skill/de0be6e8-644c-4cc9-9c8d-925dd98dda56",
            "http://data.europa.eu/esco/skill/5ca9f3d6-d7dd-4bb1-a4ca-75c6b14b7b6f",
            "http://data.europa.eu/esco/skill/70de94f5-9575-4edd-bca7-c797b023b9d6",
            "http://data.europa.eu/esco/skill/2879e8f6-037a-49ed-ba73-6f7d7766223e",
            "http://data.europa.eu/esco/skill/755a5df2-863e-44c1-b4a2-83eb33bac492",
            "http://data.europa.eu/esco/skill/fbbcb9ab-4b95-4e57-a756-30d08b7da7a5",
            "http://data.europa.eu/esco/skill/8a30eb28-eca0-4ab6-8c05-32c47d3e0dd6",
            "http://data.europa.eu/esco/skill/f597f772-24d3-4cec-813c-cf5a7027c794",
            "http://data.europa.eu/esco/skill/401eb8d8-6daa-4f8b-90ad-60afc71eb4f8",
            "http://data.europa.eu/esco/skill/013441c1-1f13-47e9-80c4-9a53e8e1bc05",
            "http://data.europa.eu/esco/skill/a521903b-aa32-4954-9790-961b0df1261f",
            "http://data.europa.eu/esco/skill/b9a57803-7f3d-42a7-a652-03c96de1d42f",
            "http://data.europa.eu/esco/skill/aee24c19-7dfd-4011-ae95-7a2fe2f7e369",
            "http://data.europa.eu/esco/skill/417d5c34-3261-4e88-a860-d0ac725965bf",
            "http://data.europa.eu/esco/skill/4da171e5-779c-4983-a76f-91c16751e99f",
            "http://data.europa.eu/esco/skill/88564d2d-2dc9-4789-864a-f9cb73f389ec",
            "http://data.europa.eu/esco/skill/c7451027-8249-405a-bb1b-342ff0f3fb3b",
            "http://data.europa.eu/esco/skill/8ee39fb7-8b46-490e-ae21-6e8b32854dd6",
            "http://data.europa.eu/esco/skill/377861af-f966-4c87-a2db-7e99904312b9",
            "http://data.europa.eu/esco/skill/1c2978b8-bb0d-4249-9c23-877571a4dffa",
            "http://data.europa.eu/esco/skill/d8ee0b01-3d33-4a32-a07c-6a107400cac0",
            "http://data.europa.eu/esco/skill/ad59afe4-6f8a-4bc4-acfd-0f228277508a",
            "http://data.europa.eu/esco/skill/c13fb093-5ffb-491a-b8b7-fa2d42a57e2e",
            "http://data.europa.eu/esco/skill/f632251a-4078-4546-b19f-7ef528d08325",
            "http://data.europa.eu/esco/skill/3af687cb-8c7a-46ea-a730-32dbaed040c6",
            "http://data.europa.eu/esco/skill/4840338b-ac6c-486a-be1d-9ddfe00b8bdf",
            "http://data.europa.eu/esco/skill/8369c2d6-c100-4cf6-bd83-9668d8678433",
            "http://data.europa.eu/esco/skill/2f954283-4642-409a-8e9b-d6ae0a11b358",
            "http://data.europa.eu/esco/skill/54b4a91f-9db8-4b67-a20d-9b6ceb9c2315",
            "http://data.europa.eu/esco/skill/bc067d2e-e151-408f-8e34-24effd2b7fd6",
            "http://data.europa.eu/esco/skill/5ae6743f-8241-49f8-b447-0df43756cb1c",
            "http://data.europa.eu/esco/skill/2164e860-7f20-48bc-b98c-5d9f8a561550",
            "http://data.europa.eu/esco/skill/32428b21-514b-42c2-81b5-244a7804ea74",
            "http://data.europa.eu/esco/skill/7b5cb897-2ee8-450c-85a6-d73925cbd1dc",
            "http://data.europa.eu/esco/skill/1c7fbce8-741b-416a-8b34-f9900e0ddd52",
            "http://data.europa.eu/esco/skill/def007fa-5fed-4a5f-91a2-b0d7e3db1be1",
            "http://data.europa.eu/esco/skill/46ec0033-3c71-415e-bcff-b065675ba2dc",
            "http://data.europa.eu/esco/skill/061f1c12-9811-46f7-ab3e-1d28367d6c73",
            "http://data.europa.eu/esco/skill/15c522eb-cd6d-4b00-9d95-db20c0f8df88",
            "http://data.europa.eu/esco/skill/0764b28d-04ee-4e80-bf58-b737f02a63e0",
            "http://data.europa.eu/esco/skill/071a4197-06b3-4226-b9a1-efd15913280a",
            "http://data.europa.eu/esco/skill/67d37d22-a013-4d79-9185-2f97820917d6",
            "http://data.europa.eu/esco/skill/709ef32a-7435-48e2-8fa2-16e389ecab8a",
            "http://data.europa.eu/esco/skill/3e75d405-c3d1-4a66-9963-05a9397d68fb",
            "http://data.europa.eu/esco/skill/54924a2c-daca-40d3-9716-4b38ceb04f38",
            "http://data.europa.eu/esco/skill/e5609f89-0daf-40bb-bd6b-94f5d7498131",
            "http://data.europa.eu/esco/skill/e3840f20-1928-4d07-944c-d2dd2ae5cbba",
            "http://data.europa.eu/esco/skill/d56fc2b5-4b0a-4e7e-9bde-a33736f6ff18",
            "http://data.europa.eu/esco/skill/0f5374e3-0b9b-4b16-af7a-49654ce0bb15",
            "http://data.europa.eu/esco/skill/8c88d336-c249-4537-b26e-d679a85c4b9b",
            "http://data.europa.eu/esco/skill/17c9a790-5664-4673-8237-c4cf3c5a8da5",
            "http://data.europa.eu/esco/skill/c1b4aa1e-bc42-49e4-952a-0cbd45f1d57b",
            "http://data.europa.eu/esco/skill/c7d24594-0c11-4b27-9039-fe0bb903da8e",
            "http://data.europa.eu/esco/skill/0df2e216-68ae-4952-b9dc-3c2f2bc04fe3",
            "http://data.europa.eu/esco/skill/49f85a99-086c-45b6-a6fa-eb90305af7b1",
            "http://data.europa.eu/esco/skill/55c16653-343c-4d02-a9de-7d8e864a4592",
            "http://data.europa.eu/esco/skill/da042121-959d-451b-b4d0-3bbf07c1bc67",
            "http://data.europa.eu/esco/skill/697dcc9f-ae92-4506-b5f2-e770d7589f74",
            "http://data.europa.eu/esco/skill/cd733651-f332-422e-a9a1-1b19b5e98449",
            "http://data.europa.eu/esco/skill/a72408af-b488-4ad4-801b-bbf16f7a7b57",
            "http://data.europa.eu/esco/skill/1558e8f3-67c9-4be6-bf2c-c5ab7b6de5f9",
            "http://data.europa.eu/esco/skill/b997afc1-19a7-4208-8010-fa0ffef15799",
            "http://data.europa.eu/esco/skill/82394321-7f35-4a29-b2fa-4a58f66258f5",
            "http://data.europa.eu/esco/skill/a8d07b5a-c1a1-42c6-9d53-db9c7a2ca996",
            "http://data.europa.eu/esco/skill/ef069fe6-305b-4452-8f7d-0c572e98effc",
            "http://data.europa.eu/esco/skill/fff0e2cd-d0bd-4b02-9daf-158b79d9688a",
            "http://data.europa.eu/esco/skill/5da8018b-ae85-4cde-ad93-0394369018f3",
            "http://data.europa.eu/esco/skill/bf942c60-8539-4951-83ee-63f770da1cb8",
            "http://data.europa.eu/esco/skill/45a16833-6666-488d-aadf-e02e569275e8",
            "http://data.europa.eu/esco/skill/0a856d55-23b5-4bdc-ad24-3eb5bc83d81f",
            "http://data.europa.eu/esco/skill/479944ef-8d2c-4279-b3d1-2b85c761fb30",
            "http://data.europa.eu/esco/skill/5da73370-f6b9-417d-a94c-09bf01f84aa2",
            "http://data.europa.eu/esco/skill/d8829a1d-dbde-435b-b921-29d6462f35c9",
            "http://data.europa.eu/esco/skill/7ffae77a-93ce-40a3-af99-fae5729e8b15",
            "http://data.europa.eu/esco/skill/0a0532c2-ee60-4410-8e07-70e4d69370ec",
            "http://data.europa.eu/esco/skill/6391d9ad-165e-4c42-80af-1b478db422e6",
            "http://data.europa.eu/esco/skill/f2c26cd0-f823-4549-a418-f84d23131308",
            "http://data.europa.eu/esco/skill/eee58e6d-4587-42f0-b9a0-fe055cfe2044",
            "http://data.europa.eu/esco/skill/6014921e-1d25-4039-adc2-04852d61880e",
            "http://data.europa.eu/esco/skill/6c153605-d62c-4970-81a9-49646ab32032",
            "http://data.europa.eu/esco/skill/ce6242a1-a2d9-43e6-a481-ae89b9ec0460",
            "http://data.europa.eu/esco/skill/13666d7d-41a8-42b6-80ba-34ea6a64667f",
            "http://data.europa.eu/esco/skill/fc6cddab-c9ce-4ce6-83ac-32b6854dd6da",
            "http://data.europa.eu/esco/skill/af8ca5cf-7fd1-4275-a886-e951c335d497",
            "http://data.europa.eu/esco/skill/14ced458-5f4b-495f-bd3d-9c240614bda0",
            "http://data.europa.eu/esco/skill/e23e81f5-18db-49aa-ad9b-b3d0fdcad1d2",
            "http://data.europa.eu/esco/skill/993b1e23-f2de-4bd8-b33f-f86dde1c8e9d",
            "http://data.europa.eu/esco/skill/28eb4689-3ba4-47a1-96b0-34a60751ea17",
            "http://data.europa.eu/esco/skill/cc608319-716f-4940-b80d-79e76d8a2cad",
            "http://data.europa.eu/esco/skill/fdc388ba-c8f4-4455-a55f-ffdf29b05f5f",
            "http://data.europa.eu/esco/skill/f0f5bb34-71df-4715-9c9e-03567b76ec8f",
            "http://data.europa.eu/esco/skill/34982b79-842f-4835-8598-d27793731d26",
            "http://data.europa.eu/esco/skill/2a051ae0-e42e-4733-8bb2-de24c4d5e3b8",
            "http://data.europa.eu/esco/skill/2b34a99f-9813-4c91-9509-b6b9b8c3132e",
            "http://data.europa.eu/esco/skill/de586dcf-d7e5-4340-bf5b-d59026f1b436",
            "http://data.europa.eu/esco/skill/1de3942b-99d8-41e8-9ef0-fbc9e9f6307b",
            "http://data.europa.eu/esco/skill/5edaf4c4-6747-4eb8-b898-72dd2351dca8",
            "http://data.europa.eu/esco/skill/0f9958d2-f700-4f62-b90b-a5873cb5ae4c",
            "http://data.europa.eu/esco/skill/bd14968e-e409-45af-b362-3495ed7b10e0",
            "http://data.europa.eu/esco/skill/7814e88f-c133-4c3b-b27f-857afa145d42",
            "http://data.europa.eu/esco/skill/e5b5053e-0ef6-4c4e-a3f2-42f955f4322c",
            "http://data.europa.eu/esco/skill/95324ac5-69a5-419b-a877-720b1ee44236",
            "http://data.europa.eu/esco/skill/d70cab7b-ad8c-4be8-84f0-c5f3f9501818",
            "http://data.europa.eu/esco/skill/21d2f96d-35f7-4e3f-9745-c533d2dd6e97",
            "http://data.europa.eu/esco/skill/440f64e5-e66c-45ee-bc29-53c14a2ad720",
            "http://data.europa.eu/esco/skill/3bddfd7c-ab6d-40c2-883d-5e97fb7640ba",
            "http://data.europa.eu/esco/skill/9bb7a97a-1a16-431e-8ea6-ad0f9f59aa0a",
            "http://data.europa.eu/esco/skill/e465a154-93f7-4973-9ce1-31659fe16dd2",
            "http://data.europa.eu/esco/skill/83b493c8-4837-41e3-9800-cff1df06cf38",
            "http://data.europa.eu/esco/skill/14fdac88-6d4a-4e47-9df5-02c6d1e86fdd",
            "http://data.europa.eu/esco/skill/07e6bc9f-9c98-44ae-a7a3-5266caa393e1",
            "http://data.europa.eu/esco/skill/44a117a9-4443-43a4-b037-23c716af671e",
            "http://data.europa.eu/esco/skill/f36fb0bd-21d5-4347-b484-69c735f4e700",
            "http://data.europa.eu/esco/skill/a9d2af37-f208-4ce8-8cc2-24de0b02b555",
            "http://data.europa.eu/esco/skill/9e14c1d8-72f2-45ba-815d-0d12e12d19fa",
            "http://data.europa.eu/esco/skill/84f58e3d-8669-4d83-8c1d-5dd1e73a654f",
            "http://data.europa.eu/esco/skill/e3201e73-ef4d-447d-ac9b-b18e45f35eea",
            "http://data.europa.eu/esco/skill/da930e2f-6047-4616-a598-bba8ecef3039",
            "http://data.europa.eu/esco/skill/62f2caf5-9f94-43a6-ac1c-5962de81643f",
            "http://data.europa.eu/esco/skill/4f5ccaad-0e4f-4e9f-8246-47765564387d",
            "http://data.europa.eu/esco/skill/11eebd42-44ab-401d-8a2c-bdb9fc9beb50",
            "http://data.europa.eu/esco/skill/a06a0d41-10fa-4ba7-a988-e8b5eba966fb",
            "http://data.europa.eu/esco/skill/bedd8fa7-5a64-4134-9095-58f5709e25e4",
            "http://data.europa.eu/esco/skill/b633eb55-8f1f-4ae6-ab4c-2022ffe2cb7f",
            "http://data.europa.eu/esco/skill/b0288eea-74b3-460a-8ac5-edc4ba70b75c",
            "http://data.europa.eu/esco/skill/1110c92f-3059-445a-9436-0f4200d365f5",
            "http://data.europa.eu/esco/skill/40001d93-8937-4859-89d5-2dca53290ff4",
            "http://data.europa.eu/esco/skill/805e4fac-5325-42a2-a9e7-c6c777185a05",
            "http://data.europa.eu/esco/skill/d1fa702c-9ea8-4a60-bb76-5ca07c32b68c",
            "http://data.europa.eu/esco/skill/c4ba12d1-4698-42fa-8abb-789462ea2586",
            "http://data.europa.eu/esco/skill/59ea80e1-463a-4dba-82c6-d0b6d577d532",
            "http://data.europa.eu/esco/skill/cc716f65-a0d0-4fba-95d9-64c358f697c0",
            "http://data.europa.eu/esco/skill/7961413f-61d0-4722-9cd9-20a050a29899",
            "http://data.europa.eu/esco/skill/06358891-8424-43c5-891e-d40f226bef40",
            "http://data.europa.eu/esco/skill/c46f9312-e414-42f0-9c8b-1c1886af7b8d",
            "http://data.europa.eu/esco/skill/f6e32a5b-bd38-4dd2-87e5-24d56152bcc6",
            "http://data.europa.eu/esco/skill/6a2bc03b-e30d-429b-9549-5784cb92ae2f",
            "http://data.europa.eu/esco/skill/d0e438df-18fb-4d70-b661-09a4e879e0db",
            "http://data.europa.eu/esco/skill/baf1fadb-975a-42be-9744-d8ba02304d09",
            "http://data.europa.eu/esco/skill/ac516e9b-8bfa-4b72-8e3e-340b0a3a51d5",
            "http://data.europa.eu/esco/skill/3be6b84a-fa78-42b9-a8df-150cdb40b9d7",
            "http://data.europa.eu/esco/skill/913e4f09-6b22-4f36-9a32-b4508ec824fe",
            "http://data.europa.eu/esco/skill/14029d35-3831-4176-be75-da64e6024e6f",
            "http://data.europa.eu/esco/skill/b8c01891-e3df-4a4b-948d-95b45e1788f5",
            "http://data.europa.eu/esco/skill/0ebd8359-48bc-4b28-adb1-450774e5f683",
            "http://data.europa.eu/esco/skill/03a74eee-2dc6-4147-8667-5cdeb65f122d",
            "http://data.europa.eu/esco/skill/446d29ed-9eaa-4798-bb91-5f196651ad0a",
            "http://data.europa.eu/esco/skill/440d5784-52a5-4729-8b04-29f978bd896d",
            "http://data.europa.eu/esco/skill/0af062de-eb43-41e9-9b96-249e2cd22d26",
            "http://data.europa.eu/esco/skill/d1ed5b3a-cab5-4c5d-baf8-0214998b58fb",
            "http://data.europa.eu/esco/skill/4c016b68-4116-468c-9dc6-42710c239e4a",
            "http://data.europa.eu/esco/skill/eda4d727-4374-49af-ace4-118c7c906835",
            "http://data.europa.eu/esco/skill/eb275d0d-64bb-49cb-a501-72aff776088f",
            "http://data.europa.eu/esco/skill/3e7516dc-0f7c-4686-8b2f-fade4e5d21be",
            "http://data.europa.eu/esco/skill/71c2f41e-14de-4b23-82ad-5dbcd54ba5b0",
            "http://data.europa.eu/esco/skill/4216e465-7baa-4884-a241-54b197bb9278",
            "http://data.europa.eu/esco/skill/cc7370dd-69fa-4c67-a96f-d4d135d38700",
            "http://data.europa.eu/esco/skill/1fa420ed-5378-4258-8d7a-9ec274dda927",
            "http://data.europa.eu/esco/skill/d0c6d77e-cb25-4770-bf77-2073fc5f7523",
            "http://data.europa.eu/esco/skill/a1388163-462a-4b76-8649-67470ea858a1",
            "http://data.europa.eu/esco/skill/c8ca84c8-7eb7-493f-9178-729f46b7591a",
            "http://data.europa.eu/esco/skill/812b8819-9b7a-4df9-9c9f-1b60888d0861",
            "http://data.europa.eu/esco/skill/a48e97cc-9240-46e3-b62d-a6935f132bea",
            "http://data.europa.eu/esco/skill/c0502e2a-1e12-422e-994e-cf9bf814e3b2",
            "http://data.europa.eu/esco/skill/76415d7f-0fde-4364-b45f-5c044580d2aa",
            "http://data.europa.eu/esco/skill/925463a7-d51f-4d5b-9f79-4d28cf30acde",
            "http://data.europa.eu/esco/skill/40802f0d-d400-404c-bc26-f9ba4eb6195a",
            "http://data.europa.eu/esco/skill/b11fe9a5-4e80-4cb8-a902-7d53a6b28b47",
            "http://data.europa.eu/esco/skill/85f46538-ae70-498a-bfbc-b8ddafe96c7d",
            "http://data.europa.eu/esco/skill/5120f979-6450-4132-874a-7f7679c7ca14",
            "http://data.europa.eu/esco/skill/5b9cde20-f1b9-4adc-bfb3-dbf70b14138d",
            "http://data.europa.eu/esco/skill/16636767-c215-4cf0-b126-e144fea20926",
            "http://data.europa.eu/esco/skill/6a57def2-7b11-4b50-95b8-b16498795ad9",
            "http://data.europa.eu/esco/skill/114c9698-c999-4369-8498-81bf641fe871",
            "http://data.europa.eu/esco/skill/af529b5d-a055-4dfa-a386-1b1c1c8670e3",
            "http://data.europa.eu/esco/skill/a47d194d-0ffd-4aaa-b695-fbbe0255000e",
            "http://data.europa.eu/esco/skill/b6d14ab8-3441-4cbe-8a0e-5b4f7f994730",
            "http://data.europa.eu/esco/skill/24c200e5-be20-4370-a137-ab53797f3a17",
            "http://data.europa.eu/esco/skill/906323f4-00c4-4c3b-ab5a-8af77be3456e",
            "http://data.europa.eu/esco/skill/97272b3e-d497-4420-893d-612b15d377d9",
            "http://data.europa.eu/esco/skill/2672f6a9-1e70-43c5-859d-e6a0fc9ea0f1",
            "http://data.europa.eu/esco/skill/2450c3b3-e78e-435b-b84d-e05d984e71dc",
            "http://data.europa.eu/esco/skill/0e8d16c9-e2db-4862-ac06-1567f01ef806",
            "http://data.europa.eu/esco/skill/cf310cff-0d28-4dbc-9dbb-cc500a3196c2",
            "http://data.europa.eu/esco/skill/6d289e8b-2cc1-4eda-ab1f-ad1090ef98f0",
            "http://data.europa.eu/esco/skill/53141450-2fa9-4890-a64e-7ffd4e1144be",
            "http://data.europa.eu/esco/skill/a7242a8d-3762-42b7-b430-3369351bbd7d",
            "http://data.europa.eu/esco/skill/4d85b881-e490-4b4c-897a-2faa4ef53956",
            "http://data.europa.eu/esco/skill/6b6a9252-2ed7-4862-a02f-1578f4a532ea",
            "http://data.europa.eu/esco/skill/9707091a-323b-4240-877a-5999a19286f0",
            "http://data.europa.eu/esco/skill/a80fb090-63f4-4b05-83a5-2f090deb7757",
            "http://data.europa.eu/esco/skill/9ce7c497-a019-435b-90ca-27b6889c2cb9",
            "http://data.europa.eu/esco/skill/fecf8a0d-62c4-4e71-9b03-0f4fc2ad7bf5",
            "http://data.europa.eu/esco/skill/c2218533-3e2a-4f50-b4fd-afde71dcf442",
            "http://data.europa.eu/esco/skill/43ae58b9-5e56-4524-b45a-b422777a0576",
            "http://data.europa.eu/esco/skill/b2f9eb81-eda4-49a9-8453-3ba345da2d98",
            "http://data.europa.eu/esco/skill/3dc6fd8e-6ff7-432e-a262-04d487858efb",
            "http://data.europa.eu/esco/skill/47a2917b-a771-4687-90fc-5562523cf4a6",
            "http://data.europa.eu/esco/skill/83b33952-3872-46ec-9a2e-b28f91303a7e",
            "http://data.europa.eu/esco/skill/0f00f63f-3ab4-4057-b92f-500584b51757",
            "http://data.europa.eu/esco/skill/ae4f0cc6-e0b9-47f5-bdca-2fc2e6316dce",
            "http://data.europa.eu/esco/skill/6b21150f-e704-4758-b102-63fd3412543c",
            "http://data.europa.eu/esco/skill/26fa7a33-a426-4cd5-9792-33bf668612e1",
            "http://data.europa.eu/esco/skill/cdd8b6b2-2fd9-453c-8d62-8a1dc6efcd49",
            "http://data.europa.eu/esco/skill/ae6c1227-dd33-4df0-a64e-439cfdf948ea",
            "http://data.europa.eu/esco/skill/e8dd9cc1-c83d-45ec-a7b0-e3727ab90336",
            "http://data.europa.eu/esco/skill/81b082fb-9a00-4660-976a-10c985979100",
            "http://data.europa.eu/esco/skill/d4789070-2ff7-4bbb-94ca-8bb1739826a9",
            "http://data.europa.eu/esco/skill/a32c2a6d-02ea-42ec-b2b7-66659ce1811c",
            "http://data.europa.eu/esco/skill/9ef0f3a0-9ce2-4ef1-a987-0366b5cb2dbe",
            "http://data.europa.eu/esco/skill/a4bead49-86a0-4849-8b6a-77cdcc157808",
            "http://data.europa.eu/esco/skill/5c845ff8-b3f7-42b1-a846-a4238a8b404e",
            "http://data.europa.eu/esco/skill/6e4f264a-e2e1-42fb-9866-e64069f8b441",
            "http://data.europa.eu/esco/skill/d1455e46-2d0d-4661-86ab-3cb7a0c46d02",
            "http://data.europa.eu/esco/skill/f5ac9226-0ace-4d34-a1c3-7b3845834a98",
            "http://data.europa.eu/esco/skill/99814cb0-5f6c-4663-99a4-af28b11d5d2f",
            "http://data.europa.eu/esco/skill/5063a99f-975b-4a7c-85e3-69ecb375b4d3",
            "http://data.europa.eu/esco/skill/38716afc-a93b-44ab-96cc-2ecf67edcf32",
            "http://data.europa.eu/esco/skill/024037ca-96a9-4cb8-ba86-2cd5a209c90a",
            "http://data.europa.eu/esco/skill/8deb6cb7-15e3-4fd7-a9eb-644c23de95fa",
            "http://data.europa.eu/esco/skill/ccdbd9bb-4faf-403c-a968-e8bf487f8a53",
            "http://data.europa.eu/esco/skill/6e8fe5de-fe6d-482c-92fe-20e56ee57324",
            "http://data.europa.eu/esco/skill/eb2ae1c7-ba57-4695-8f29-9dd41c26e01e",
            "http://data.europa.eu/esco/skill/1fc03dc8-0ee0-4876-83ec-86e0bfcb3876",
            "http://data.europa.eu/esco/skill/1ebc1454-b6cb-4e45-a96a-17399961ad39",
            "http://data.europa.eu/esco/skill/4350c38d-0fe9-4ca7-bab9-40ed7f72b04f",
            "http://data.europa.eu/esco/skill/afd63e8c-a4e7-42d3-86c9-3b2dd549a353",
            "http://data.europa.eu/esco/skill/87f31087-4c7c-4a71-b7b8-4039d94b4c12",
            "http://data.europa.eu/esco/skill/0256f6b0-f4a9-4c5e-9cfd-96d58a2a70fe",
            "http://data.europa.eu/esco/skill/23b45aa6-a479-486d-9e6f-062cbab7c68a",
            "http://data.europa.eu/esco/skill/c2c1b595-20f5-4a13-885a-bcc1b7760e3e",
            "http://data.europa.eu/esco/skill/1a4cc54f-1e53-442b-a6d2-1682dc8ef8f9",
            "http://data.europa.eu/esco/skill/1c8327dd-9af0-4124-9c4e-0342d90189d4",
            "http://data.europa.eu/esco/skill/34928d4f-16e2-46a7-937a-c46838f550eb",
            "http://data.europa.eu/esco/skill/a87c1c42-8927-4183-97c7-a17554c22b29",
            "http://data.europa.eu/esco/skill/b4dc6e4f-dc7d-445f-8ce2-d7b9d225e282",
            "http://data.europa.eu/esco/skill/83218cac-3599-417e-93d2-26013cdccd98",
            "http://data.europa.eu/esco/skill/51586df8-1c46-4b47-8583-773cb63bf00b",
            "http://data.europa.eu/esco/skill/ddfe79e2-c2e3-4ba7-9bdf-3820b4f75b54",
            "http://data.europa.eu/esco/skill/fafe0b9d-dbd9-46bd-b770-26299539ce66",
            "http://data.europa.eu/esco/skill/6b9c6280-cd0b-4462-885e-da63fe5918ea",
            "http://data.europa.eu/esco/skill/a12057b4-6d11-4a12-ab8e-15a028ef0a6d",
            "http://data.europa.eu/esco/skill/76ef6ed3-1658-4a1a-9593-204d799c6d0c",
            "http://data.europa.eu/esco/skill/0d7f2cce-245a-4d02-9b8d-2e9946cc4c5d",
            "http://data.europa.eu/esco/skill/23df3447-f59e-45a4-86da-78e38d07c808",
            "http://data.europa.eu/esco/skill/dda69c90-2355-4ab5-b0ef-6ada47f74cc1",
            "http://data.europa.eu/esco/skill/cf532795-a1fd-43bd-8407-6e43f877e6e3",
            "http://data.europa.eu/esco/skill/c022ff1b-7783-4e64-89ef-0422c81ff51f",
            "http://data.europa.eu/esco/skill/116083f9-8d05-4b2e-a15f-287b18de5f67",
            "http://data.europa.eu/esco/skill/19c0e68e-6e6c-4698-85dc-ef66a9de2c37",
            "http://data.europa.eu/esco/skill/162c7407-14ec-43ef-a4c2-31ccd1267850",
            "http://data.europa.eu/esco/skill/a2842c30-be76-4327-9dce-ff56cf830533",
            "http://data.europa.eu/esco/skill/40c815cd-30a2-42e5-a549-bf9017cb6df3",
            "http://data.europa.eu/esco/skill/36500a31-3204-45b2-bf75-5cfe06d9cea2",
            "http://data.europa.eu/esco/skill/7c5b66c6-36a1-4e03-bb57-64e8688b8f58",
            "http://data.europa.eu/esco/skill/9a8f5432-fb04-498d-9ba4-a4e1444ff056",
            "http://data.europa.eu/esco/skill/a7f0fbe0-c546-4f30-8e41-34a58c64567e",
            "http://data.europa.eu/esco/skill/ec95d0a4-886e-4eb1-aa4c-2d5f2549df7d",
            "http://data.europa.eu/esco/skill/eb3aa8c1-fb14-47b4-a352-b7975abd2480",
            "http://data.europa.eu/esco/skill/7482a123-e801-48de-9733-262125671410",
            "http://data.europa.eu/esco/skill/b8eb2517-37d8-43e6-9856-9c29d5c51fde",
            "http://data.europa.eu/esco/skill/ce354460-500e-4eaa-8e95-8f243fcea3db",
            "http://data.europa.eu/esco/skill/6e3e5c17-0d46-4376-9928-8dbaaea27164",
            "http://data.europa.eu/esco/skill/797a4416-0b8d-44f3-8fcd-eb10bd0be6b4",
            "http://data.europa.eu/esco/skill/14764a75-3104-4875-ba1d-57c059b9b013",
            "http://data.europa.eu/esco/skill/27ddcef9-8522-4699-b0e6-2e2b10fd6d13",
            "http://data.europa.eu/esco/skill/b9e451f5-a62f-4a16-a229-255fa5c222a5",
            "http://data.europa.eu/esco/skill/a0bf9b9d-e53f-418c-a839-5d99eff718fe",
            "http://data.europa.eu/esco/skill/e5a6e1e0-1b07-4432-83ba-77d593b2cb47",
            "http://data.europa.eu/esco/skill/ab67fbfb-0ca7-4345-8bf6-af8f59480212",
            "http://data.europa.eu/esco/skill/bd08f332-a295-4a59-a13a-05b08ca3761b",
            "http://data.europa.eu/esco/skill/0de61385-de6d-4146-ba32-1cc1bc102220",
            "http://data.europa.eu/esco/skill/de9f85ba-e77f-48fd-8c66-f5ebaf32d655",
            "http://data.europa.eu/esco/skill/000f1d3d-220f-4789-9c0a-cc742521fb02",
            "http://data.europa.eu/esco/skill/b34e2ba1-9080-48c9-9b42-ee9192a4d3f1",
            "http://data.europa.eu/esco/skill/19a8293b-8e95-4de3-983f-77484079c389",
            "http://data.europa.eu/esco/skill/63f0aa84-5e4c-4eb6-8e8c-876d9feedf1f",
            "http://data.europa.eu/esco/skill/487fd260-be20-4980-a8e4-b819f0b549f1",
            "http://data.europa.eu/esco/skill/add193d6-b7bf-40ab-98ff-4a4c6c807387",
            "http://data.europa.eu/esco/skill/8fa510d0-a8a6-4932-a361-00f8442cfe23",
            "http://data.europa.eu/esco/skill/5898d99a-62a4-4e10-a2e3-0d815ce44248",
            "http://data.europa.eu/esco/skill/c330db31-7fa9-4a8c-8ad6-baa957bcfed1",
            "http://data.europa.eu/esco/skill/5608d5a0-6d5e-43b7-be37-616501729bb4",
            "http://data.europa.eu/esco/skill/14d1e367-3efe-4ec5-86ae-eca48710ee4e",
            "http://data.europa.eu/esco/skill/9ff9db9d-d14b-426e-83f3-e7449af6c79f",
            "http://data.europa.eu/esco/skill/541561bc-510c-4a99-881c-2d8bf5a85462",
            "http://data.europa.eu/esco/skill/bf4d884f-c848-402a-b130-69c266b04164",
            "http://data.europa.eu/esco/skill/1c00bc98-2713-4c62-82c5-6d86f2e6a4cb",
            "http://data.europa.eu/esco/skill/d822b4b2-26c7-4bd9-970d-229c6504f615",
            "http://data.europa.eu/esco/skill/7c9cb75d-ca4a-47c0-a989-ec0ba65a4bf0",
            "http://data.europa.eu/esco/skill/dbab7fdd-b99b-4233-bc81-75563eee51a4",
            "http://data.europa.eu/esco/skill/1b70a55d-b8a4-49fc-96c6-15ab4cff2522",
            "http://data.europa.eu/esco/skill/9c48a5cc-d4a5-4cef-93f7-504902f35319",
            "http://data.europa.eu/esco/skill/4dc5042a-b2b4-4ad5-a39b-3558b073cec7",
            "http://data.europa.eu/esco/skill/07e47d58-dd0e-4302-9e68-70a188e89065",
            "http://data.europa.eu/esco/skill/2962214b-cc38-4d20-aa8b-aa120091d743",
            "http://data.europa.eu/esco/skill/770d4956-b5e0-4d44-bf42-3a766b232c5d",
            "http://data.europa.eu/esco/skill/7d10fcb2-b368-48ab-996b-7c9fafcf68ed",
            "http://data.europa.eu/esco/skill/52739347-5eaa-4c9e-8cde-af7d2ba83320",
            "http://data.europa.eu/esco/skill/debef2fc-7af4-4bff-ac92-4d0317915a84",
            "http://data.europa.eu/esco/skill/2b6bc32d-c60d-435b-931f-79bd3404f8b9",
            "http://data.europa.eu/esco/skill/eb0e5615-1575-4a86-a1a2-7d39595033c5",
            "http://data.europa.eu/esco/skill/020918ef-4b55-47c1-8430-ab7b8f6a7377",
            "http://data.europa.eu/esco/skill/53780150-1581-4ae6-b435-34068c172caf",
            "http://data.europa.eu/esco/skill/5354a726-ee64-4f34-90f8-8436f9f374b8",
            "http://data.europa.eu/esco/skill/6fe76a8b-4d5f-4cea-b76b-5908463fb014",
            "http://data.europa.eu/esco/skill/505e4ef3-7ce4-437d-b7b4-5c608f71c258",
            "http://data.europa.eu/esco/skill/a4b2a278-2123-4450-8b43-be01c4f341da",
            "http://data.europa.eu/esco/skill/7957b5f9-1f33-4ff8-84e9-e258b17882c2",
            "http://data.europa.eu/esco/skill/d89b9a3f-19bf-4b9b-9664-ed35924eb399",
            "http://data.europa.eu/esco/skill/6f993130-082b-4732-b62f-52cdf2096ad9",
            "http://data.europa.eu/esco/skill/f40da95c-febe-406c-96aa-2d6d9c09a0a3",
            "http://data.europa.eu/esco/skill/7aa52bdc-9c56-4341-be65-e8e30f64403f",
            "http://data.europa.eu/esco/skill/09f2f811-a3fb-4de3-a70f-6420a6935575",
            "http://data.europa.eu/esco/skill/8359bd04-d2cb-4757-af1f-39a921df9efe",
            "http://data.europa.eu/esco/skill/82de2df7-0d6c-42b1-94ac-b3e9a029e917",
            "http://data.europa.eu/esco/skill/727eb829-28da-4d6a-8552-ba5b5d280c41",
            "http://data.europa.eu/esco/skill/61c567d0-e01c-4f5c-9420-5926298f5099",
            "http://data.europa.eu/esco/skill/768fe91b-ccde-4649-94f3-07be5d8b6be1",
            "http://data.europa.eu/esco/skill/2c28fc20-8a60-4311-8899-5ef8729c05b3",
            "http://data.europa.eu/esco/skill/f7e051fc-8f7b-45b3-8911-8ffb9b951f4a",
            "http://data.europa.eu/esco/skill/3f4dab51-572b-4e2c-85ef-3b4c3f7094e1",
            "http://data.europa.eu/esco/skill/022dc430-872a-473a-9cf4-fed4895e58ad",
            "http://data.europa.eu/esco/skill/d8726a5a-0614-4dc6-ad03-cf5e3b3caaa5",
            "http://data.europa.eu/esco/skill/6c80d53c-d8c9-41fe-998f-091fca208834",
            "http://data.europa.eu/esco/skill/58783f72-5098-4807-b1d1-954ec2ae94c4",
            "http://data.europa.eu/esco/skill/81086bb0-a110-470d-ba5a-2c68fc535407",
            "http://data.europa.eu/esco/skill/4bd3cd7d-b061-4cc2-ba8d-3262065ebbf0",
            "http://data.europa.eu/esco/skill/9d1b08b3-ba1e-41f6-a466-1ac1e62eb5f0",
            "http://data.europa.eu/esco/skill/c8de8023-ab8a-402b-a828-b2b57cd3b2f7",
            "http://data.europa.eu/esco/skill/12c82224-6394-4c3c-9831-2a8198ed1274",
            "http://data.europa.eu/esco/skill/ca8fae74-46ff-4e1b-94ef-ce22393e02e7",
            "http://data.europa.eu/esco/skill/749ae61c-fe7b-461b-8bc3-349f46617ee7",
            "http://data.europa.eu/esco/skill/2a69d6a0-b90f-4d50-bef8-a05d6704575d",
            "http://data.europa.eu/esco/skill/cdfb75ab-adc3-4143-a453-b91949dcfa1f",
            "http://data.europa.eu/esco/skill/ec25e825-8c5b-4b46-950b-dcb87dd410e7",
            "http://data.europa.eu/esco/skill/04cf164c-d07d-49cf-a1b3-84bdbd79b5cb",
            "http://data.europa.eu/esco/skill/1c43679a-c6fc-4b42-bb8b-452a34ae9b73",
            "http://data.europa.eu/esco/skill/5645b349-2b67-48cf-b290-bbb029e59dcf",
            "http://data.europa.eu/esco/skill/7ca5d3c2-2cea-4fac-88e4-f2955c5b705c",
            "http://data.europa.eu/esco/skill/d83e8dbd-257b-4aa9-abb5-e65924eb1797",
            "http://data.europa.eu/esco/skill/d5464c09-f8b7-44b1-b216-db2bdc80dc17",
            "http://data.europa.eu/esco/skill/4ff549c3-daa5-4def-9916-301c7bdf13b8",
            "http://data.europa.eu/esco/skill/f9e788df-d68a-4f92-92c7-80e135fc35ff",
            "http://data.europa.eu/esco/skill/913e7e83-b8f8-4574-b1ca-1b38f3fd974a",
            "http://data.europa.eu/esco/skill/359b5031-74da-418f-bd97-6f4e66eb9851",
            "http://data.europa.eu/esco/skill/70b0bc44-6751-4530-ac5c-a893d4241aeb",
            "http://data.europa.eu/esco/skill/a2b5dcf3-5b6a-453d-876c-cff540c0faf1",
            "http://data.europa.eu/esco/skill/1f1bef0f-2484-4136-a5bf-6ed80346a671",
            "http://data.europa.eu/esco/skill/0ba0717c-3a7a-45a3-834c-59c554e56c72",
            "http://data.europa.eu/esco/skill/e81d7bed-b7c7-41bf-a30a-e3201e8f09b3",
            "http://data.europa.eu/esco/skill/a35b9b1c-ebfe-41ac-96be-2a7b2ea04670",
            "http://data.europa.eu/esco/skill/8ff314a8-57d7-4b8c-bf41-79f9572fedd0",
            "http://data.europa.eu/esco/skill/7106b5df-e017-4c28-8c40-98814db5b775",
            "http://data.europa.eu/esco/skill/398b4b25-fb61-4003-9293-50914d8bfcba",
            "http://data.europa.eu/esco/skill/0da6cab1-0ee9-4391-a299-f71fbf21db56",
            "http://data.europa.eu/esco/skill/670f8eea-f25b-4c3f-97ef-a4729d4c63d6",
            "http://data.europa.eu/esco/skill/7e1f9657-ab4e-407c-842f-b846197060e3",
            "http://data.europa.eu/esco/skill/20c920d4-7ff7-4676-ba61-3f04490d9416",
            "http://data.europa.eu/esco/skill/74cb80c4-df6b-4d79-a153-cb7b3b3b6eb0",
            "http://data.europa.eu/esco/skill/c062bab3-3ea0-4291-9220-a2d8fef4bead",
            "http://data.europa.eu/esco/skill/e5beeff8-7c98-490a-ae2c-16fbb6a87305",
            "http://data.europa.eu/esco/skill/b9aad733-959a-4178-984f-53e4bd05b420",
            "http://data.europa.eu/esco/skill/b105ec9b-0857-41d6-8d07-a83e58b73d90",
            "http://data.europa.eu/esco/skill/744442ac-c157-4350-8be0-ce454df4f5c5",
            "http://data.europa.eu/esco/skill/03ff0d53-573a-47a0-a0ad-1995815a4339",
            "http://data.europa.eu/esco/skill/2bde42ae-e776-41c1-9ded-b07b30bfe985",
            "http://data.europa.eu/esco/skill/c5ed451b-ef39-47a3-aee7-8f5be0aa8431",
            "http://data.europa.eu/esco/skill/933efc5f-a797-4581-8a8b-c08956ff7d58",
            "http://data.europa.eu/esco/skill/54ae8c54-2aad-46fc-bff2-9d44d0ec7a6e",
            "http://data.europa.eu/esco/skill/3c6fe585-7d12-4315-b579-e30c042088ce",
            "http://data.europa.eu/esco/skill/3c1af34b-4562-4c39-a30e-0812b84a147e",
            "http://data.europa.eu/esco/skill/a71577b2-7550-4222-88ee-dcbd2d881516",
            "http://data.europa.eu/esco/skill/9d2e926f-53d9-41f5-98f3-19dfaa687f3f",
            "http://data.europa.eu/esco/skill/9bc3b9ae-50d8-4a32-857e-8cf8d9b4ba8e",
            "http://data.europa.eu/esco/skill/6efef9df-ac86-45ca-8b1b-a8178ef17b5c",
            "http://data.europa.eu/esco/skill/4f6b656a-54d0-48ce-9b55-ebcac5904a14",
            "http://data.europa.eu/esco/skill/0c66036a-3bdd-4955-8343-985f4697af64",
            "http://data.europa.eu/esco/skill/fbafa41f-cd05-4109-a649-8b44d306d779",
            "http://data.europa.eu/esco/skill/818c2b89-1335-4443-ada7-e1046862e273",
            "http://data.europa.eu/esco/skill/b820c9de-4d00-4e79-9af9-33f26044ff42",
            "http://data.europa.eu/esco/skill/c31a222b-de0b-490c-82b8-472167cb0ba5",
            "http://data.europa.eu/esco/skill/6f8a40d6-f9ce-43ec-a72f-d4213a53f3ed",
            "http://data.europa.eu/esco/skill/97bdd757-29d4-4126-92a6-07608825aa3b",
            "http://data.europa.eu/esco/skill/cf200547-25b5-47c8-9eda-bca4aedc13fb",
            "http://data.europa.eu/esco/skill/69cfc5ed-6569-4aca-a4cc-fd782ba51d9c",
            "http://data.europa.eu/esco/skill/70988d2c-a750-4808-a360-5aaf859cf39a",
            "http://data.europa.eu/esco/skill/6a322874-e32f-4cd8-9683-badce67a7f73",
            "http://data.europa.eu/esco/skill/d32db06e-bd06-4415-a7ab-1b0ee68caa9a",
            "http://data.europa.eu/esco/skill/2891f0dc-4426-4a9a-8fbb-db4fb0a66f91",
            "http://data.europa.eu/esco/skill/7fb699d9-182a-430e-b7a0-6d8ed05c284b",
            "http://data.europa.eu/esco/skill/e665d919-f28d-4ac4-adf0-8903ac66540e",
            "http://data.europa.eu/esco/skill/7bf4539a-3f2e-4485-876b-1c9306b181af",
            "http://data.europa.eu/esco/skill/c6c37f68-190e-4482-905c-93007a64ed61",
            "http://data.europa.eu/esco/skill/b916d60e-38e9-4241-914a-4e2144bda711",
            "http://data.europa.eu/esco/skill/a06aa85f-2879-4a02-a6bd-cd60cdfc3f12",
            "http://data.europa.eu/esco/skill/09f4c52b-ac72-4f0e-a8ae-78bb1d7bffba",
            "http://data.europa.eu/esco/skill/d56358e4-3bcc-4b5d-981d-ea15b2743e9e",
            "http://data.europa.eu/esco/skill/52e3507b-1cad-409f-9146-3c5e2afe7df9",
            "http://data.europa.eu/esco/skill/ffddfc7c-a9dd-449f-9e96-882dc447c8b6",
            "http://data.europa.eu/esco/skill/0fb753a1-2ef2-4429-84d2-ecb2b24daf7a",
            "http://data.europa.eu/esco/skill/04fe962b-4017-4eb7-9139-7d69b6922bc9",
            "http://data.europa.eu/esco/skill/e4d34eaf-742e-4bba-801d-e8d617f1f419",
            "http://data.europa.eu/esco/skill/d6cdef49-b669-4507-91d1-3f959cf98e47",
            "http://data.europa.eu/esco/skill/6fa1c2c0-a012-4ca0-9642-e01569ba322c",
            "http://data.europa.eu/esco/skill/b3c8eada-b0a8-4b61-968b-b354fbfb6874",
            "http://data.europa.eu/esco/skill/772a9c72-abef-4333-9788-f4cb48ad8e08",
            "http://data.europa.eu/esco/skill/93a2f730-d5f6-4604-a25e-a2ee206ea468",
            "http://data.europa.eu/esco/skill/6b643893-0a1f-4f6c-83a1-e7eef75849b9",
            "http://data.europa.eu/esco/skill/e4d90d50-bd28-4a0c-a3a7-d4963e2a307a",
            "http://data.europa.eu/esco/skill/172020d1-e151-445b-8173-e2a5fb16fe51",
            "http://data.europa.eu/esco/skill/f0010b93-5552-45cb-ad26-168c8168f319",
            "http://data.europa.eu/esco/skill/b78f324d-e16c-43f8-97d2-50d3a7cd1fb3",
            "http://data.europa.eu/esco/skill/f2d57f41-43b4-4f5b-8100-3df5c21eda50",
            "http://data.europa.eu/esco/skill/fb601189-afa9-4a7b-8430-21a906483072",
            "http://data.europa.eu/esco/skill/5d852752-4bae-4c6e-ab2a-7eb6bf82a99d",
            "http://data.europa.eu/esco/skill/42ed3bfb-1a01-4c8b-9758-fc6438865734",
            "http://data.europa.eu/esco/skill/42cb7669-c371-4903-9c0b-13db67b2e4bb",
            "http://data.europa.eu/esco/skill/5f782745-6382-4873-b415-8fb91c705b03",
            "http://data.europa.eu/esco/skill/143769cb-b61e-47d8-a61e-eedfbec1016c",
            "http://data.europa.eu/esco/skill/af7ae54f-4649-4c16-87c7-59ba41d4d57f",
            "http://data.europa.eu/esco/skill/3e23db60-0c3d-498a-a6ac-ffbed0ecb033",
            "http://data.europa.eu/esco/skill/fe1c2b32-7fe9-4668-affa-07ff658a68cf",
            "http://data.europa.eu/esco/skill/fae7e545-5114-43cc-8e83-178e2549ecb9",
            "http://data.europa.eu/esco/skill/4f1afd9d-77a7-40f8-a3c7-fe18b984ce43",
            "http://data.europa.eu/esco/skill/0ce96a86-7a28-4c44-9e59-591375310894",
            "http://data.europa.eu/esco/skill/bb99af26-71ff-4dca-bde7-1eb1f6194426",
            "http://data.europa.eu/esco/skill/7253d98c-4a6d-4e37-8b6c-accca9f4b639",
            "http://data.europa.eu/esco/skill/eeca3780-8049-499f-a268-95a7ad26642c",
            "http://data.europa.eu/esco/skill/4a2a63e6-5c62-43a0-a375-fdf4c2805270",
            "http://data.europa.eu/esco/skill/7fc4c18a-68f3-425a-aadf-f83633be47a1",
            "http://data.europa.eu/esco/skill/9b30c8fd-6467-4d16-99e0-32899a9ceec0",
            "http://data.europa.eu/esco/skill/484d048c-a5d1-46a5-b57f-45b69c0ac552",
            "http://data.europa.eu/esco/skill/3ff589b7-68df-4ea5-ae41-b395bdb2378f",
            "http://data.europa.eu/esco/skill/be149bed-a789-48ee-832b-620ccf776af9",
            "http://data.europa.eu/esco/skill/bb979b3b-8292-409f-8c27-b17e13bab8bb",
            "http://data.europa.eu/esco/skill/58bfa8b2-f39f-40c3-8e5e-2dc0d26c7601",
            "http://data.europa.eu/esco/skill/04473991-161d-46db-8684-0d5ed6cb6c66",
            "http://data.europa.eu/esco/skill/ee6c325b-223e-432f-89c3-a0f135adac9e",
            "http://data.europa.eu/esco/skill/2b544b2c-f22d-40e6-8578-3cb65554643a",
            "http://data.europa.eu/esco/skill/ed8de897-adbe-4f0e-b4d2-534953e64c72",
            "http://data.europa.eu/esco/skill/1c8658c0-8f1e-4bf1-8e81-0a3a6033847e",
            "http://data.europa.eu/esco/skill/8088750d-8388-4170-a76f-48354c469c44",
            "http://data.europa.eu/esco/skill/3ce1fe19-7f9e-4070-941d-651673a5693b",
            "http://data.europa.eu/esco/skill/be64bb4c-84ac-4c83-8cc7-fc8593e15136",
            "http://data.europa.eu/esco/skill/60f2ec1a-9237-4d0e-8c4a-1a3e67a0d83d",
            "http://data.europa.eu/esco/skill/64852ade-6ae1-4760-ba4a-976bf07ca255",
            "http://data.europa.eu/esco/skill/966f2fd3-3de6-42da-b87c-da924c6d7960",
            "http://data.europa.eu/esco/skill/897b393f-e7e0-4248-a40d-d77119694e83",
            "http://data.europa.eu/esco/skill/91ba97f5-7518-4638-b2d2-308996db3cf8",
            "http://data.europa.eu/esco/skill/0a776a56-6ec1-4790-84b3-46d0be706806",
            "http://data.europa.eu/esco/skill/968ea96d-42d0-44fe-94b0-f11d84a8234d",
            "http://data.europa.eu/esco/skill/c1f29568-f2bb-49d2-b6c2-28748bd1629b",
            "http://data.europa.eu/esco/skill/8ff015a0-98ea-4d80-a23e-1f68110a919e",
            "http://data.europa.eu/esco/skill/05010f6c-0b40-4525-ac4a-057d46a2b6f4",
            "http://data.europa.eu/esco/skill/20c6a2ec-ef98-4f95-8a16-c8006d9242ba",
            "http://data.europa.eu/esco/skill/d1a86399-24d8-415f-98b9-e8cbb6b04a26",
            "http://data.europa.eu/esco/skill/062320e5-79d1-45e8-8b7e-5deeb53279a2",
            "http://data.europa.eu/esco/skill/1859883d-c047-4fe1-8e74-7bb4385d6ad2",
            "http://data.europa.eu/esco/skill/eabc06f6-2daf-45bb-b525-7fc078e2eadf",
            "http://data.europa.eu/esco/skill/27065048-bca8-4b0a-891a-b192b9ceadfd",
            "http://data.europa.eu/esco/skill/dd1e0bc8-d54a-435a-bc43-586bac0b8c02",
            "http://data.europa.eu/esco/skill/0e361e34-c563-4892-b9d3-873a6a4fef8a",
            "http://data.europa.eu/esco/skill/978db6a1-7297-4c89-819a-47605f2c3ba8",
            "http://data.europa.eu/esco/skill/29fb0fb5-dfc4-4098-ac9b-3a712000f48f",
            "http://data.europa.eu/esco/skill/7dde2d1d-48fa-43c9-8b48-1372b6e7cb4c",
            "http://data.europa.eu/esco/skill/f6d294f4-db62-4fc5-a7b8-778e5071c112",
            "http://data.europa.eu/esco/skill/123ea020-9f87-495f-b8c3-15133646392b",
            "http://data.europa.eu/esco/skill/0aabcae7-71de-456e-8e38-d9e40a4e0272",
            "http://data.europa.eu/esco/skill/c4b626c1-e71c-44ec-9f88-042766f37fcc",
            "http://data.europa.eu/esco/skill/2a3a96a3-709e-4d60-81f6-d247d6933f13",
            "http://data.europa.eu/esco/skill/07dd856d-6141-48a7-a228-918f88494812",
            "http://data.europa.eu/esco/skill/0ccdfe98-f845-4598-84a1-3dca66e9d9a3",
            "http://data.europa.eu/esco/skill/a283db93-b160-4b6f-9855-4e79806c4674",
            "http://data.europa.eu/esco/skill/580660a6-5d3a-421d-a54f-d85b706c2b2f",
            "http://data.europa.eu/esco/skill/f8e7c608-a92d-4b61-b84f-e2453b0aa4a4",
            "http://data.europa.eu/esco/skill/8696aff0-18c0-4ba6-a4d0-9a21861b3e5e",
            "http://data.europa.eu/esco/skill/4da4587a-fd0b-4ca2-886c-c42a1562d432",
            "http://data.europa.eu/esco/skill/2eae92cf-5f21-43e7-aa41-ab65b00c1b92",
            "http://data.europa.eu/esco/skill/3c134a08-521f-489d-9ff0-d84fcdc963eb",
            "http://data.europa.eu/esco/skill/3cd569a2-4f88-4c1e-9995-8dce8c5e51a7",
            "http://data.europa.eu/esco/skill/7f27f1ff-2e17-4b69-990e-c23334478313",
            "http://data.europa.eu/esco/skill/6709e92c-7546-46a4-aa57-5e951bd4cb0f",
            "http://data.europa.eu/esco/skill/f68cd42c-1d67-4ca7-9b37-8f024763823d",
            "http://data.europa.eu/esco/skill/2b285a57-4add-453e-af5d-3d986a65c8fe",
            "http://data.europa.eu/esco/skill/b080a008-a35d-4bd0-92e9-edf3773bb2b7",
            "http://data.europa.eu/esco/skill/febcc41e-bf0c-4733-a498-c2881c86ce88",
            "http://data.europa.eu/esco/skill/a4881e54-6055-4e61-855a-0a56ced7cfa3",
            "http://data.europa.eu/esco/skill/8a16ada9-2794-4c81-bb48-5ec1cfbcf1db",
            "http://data.europa.eu/esco/skill/f96f5aa5-6ed7-40dc-a431-68b0a09d6d4b",
            "http://data.europa.eu/esco/skill/52b38e90-3d05-4042-a9e2-d19dec8dd7c1",
            "http://data.europa.eu/esco/skill/449d119b-2d66-43a9-9230-7d27f16afbb6",
            "http://data.europa.eu/esco/skill/53456d90-8c0d-4bd2-9f1b-312619b6e2a6",
            "http://data.europa.eu/esco/skill/fc1fa8eb-a7fd-481c-b3b3-5fc45b3d1e97",
            "http://data.europa.eu/esco/skill/cae4c053-05d1-413d-8499-f37793aad56e",
            "http://data.europa.eu/esco/skill/3cc1ee94-ab2c-4f35-8171-3cf4b5b664e4",
            "http://data.europa.eu/esco/skill/2f05d09d-7e91-423c-9210-19da0b0e34c6",
            "http://data.europa.eu/esco/skill/7341904f-5913-470c-8c53-bc832df248ec",
            "http://data.europa.eu/esco/skill/902fb91c-3113-4004-9b4f-79aa86b638b7",
            "http://data.europa.eu/esco/skill/0e44ceb3-9dd4-4ec4-b1d7-a0e83ddba7d4",
            "http://data.europa.eu/esco/skill/a4ec696c-ac73-4bd5-8a3c-09a783cf3944",
            "http://data.europa.eu/esco/skill/90f9dc0c-f9c0-45a7-9fb4-20a7e4ca4632",
            "http://data.europa.eu/esco/skill/bc0b0b5d-f6be-405a-b224-b6902a4dbe97",
            "http://data.europa.eu/esco/skill/ddfd922f-a4f7-47a7-bf1c-cf9124e5bf40",
            "http://data.europa.eu/esco/skill/571c8cb7-33f5-4a2e-999d-35688afed581",
            "http://data.europa.eu/esco/skill/50b100ea-74fd-4706-99db-3e4ca55e51b8",
            "http://data.europa.eu/esco/skill/bb2b4485-2bad-402f-8517-44bf7d9afff4",
            "http://data.europa.eu/esco/skill/406117ad-e035-483f-bbd3-9f6c2d95e535",
            "http://data.europa.eu/esco/skill/9b9de2a4-d8af-4a7b-933a-a8334ae60067",
            "http://data.europa.eu/esco/skill/c415ce6e-24f5-4130-add4-645b9d455c71",
            "http://data.europa.eu/esco/skill/b0096dc5-2e2d-4bc1-8172-05bf486c3968",
            "http://data.europa.eu/esco/skill/7b5cce4d-c7fe-4119-b48f-70aa05391787",
            "http://data.europa.eu/esco/skill/75d2ac62-700a-41d1-b920-5cd7aaa78553",
            "http://data.europa.eu/esco/skill/feb717ff-fba6-4a91-aa10-ee11cf2da6b4",
            "http://data.europa.eu/esco/skill/e2b05bcb-b60e-45b5-baa6-3a67ebe77538",
            "http://data.europa.eu/esco/skill/33a82b83-c838-4889-ae62-fae1317481eb",
            "http://data.europa.eu/esco/skill/557c86b8-a0bc-4545-bb30-d141b1d60bc1",
            "http://data.europa.eu/esco/skill/07e51c60-763a-4335-b4ec-70a15f66d328",
            "http://data.europa.eu/esco/skill/0e62eba3-d076-47e5-b22d-340a8faccb3b",
            "http://data.europa.eu/esco/skill/6ae8306a-4f9d-4410-ac82-6406f3cbd16a",
            "http://data.europa.eu/esco/skill/db36f7f1-5900-45bd-9ffb-f2b19c4362ac",
            "http://data.europa.eu/esco/skill/5303169c-75d6-4751-9136-a4b88343388c",
            "http://data.europa.eu/esco/skill/20eb4c72-2a50-4a68-acb0-7e0d4559c097\""};
}