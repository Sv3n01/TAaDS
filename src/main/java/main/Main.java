package main;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {

        try {
            PDDocument doc = PDDocument.load(new File("src/main/java/main/modules.pdf"));
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String text = pdfStripper.getText(doc);
            Files.write(Paths.get("src/main/java/main/result.txt"), text.getBytes());
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}