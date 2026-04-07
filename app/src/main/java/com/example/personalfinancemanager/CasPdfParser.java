package com.example.personalfinancemanager;

import android.util.Log;

import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CasPdfParser {

    private static final String TAG = "CasPdfParser";

    // ISIN pattern: INE followed by 9 alphanumeric characters
    private static final Pattern ISIN_PATTERN = Pattern.compile("(INE[A-Z0-9]{9})");

    // Quantity patterns for different CAS formats
    private static final Pattern CLOSING_BAL_PATTERN = Pattern.compile(
            "(?:Closing\\s*(?:Bal(?:ance)?[.:]?)|Total\\s*(?:Bal(?:ance)?[.:]?)|Free\\s*(?:Bal(?:ance)?[.:]?))\\s*:?\\s*([\\d,]+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    // Fallback: look for quantity as a standalone number after ISIN block
    private static final Pattern QTY_LINE_PATTERN = Pattern.compile(
            "(?:Qty|Quantity|Units|Shares)\\s*:?\\s*([\\d,]+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    // Average price pattern
    private static final Pattern AVG_PRICE_PATTERN = Pattern.compile(
            "(?:Avg\\.?\\s*(?:Price|Cost)|Average\\s*(?:Price|Cost)|Cost\\s*Price)\\s*:?\\s*(?:Rs\\.?|INR|\\u20B9)?\\s*([\\d,]+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    public static List<Stock> parse(InputStream inputStream, String password) throws Exception {
        PDDocument document = null;
        try {
            if (password != null && !password.isEmpty()) {
                document = PDDocument.load(inputStream, password);
            } else {
                document = PDDocument.load(inputStream);
            }

            PDFTextStripper stripper = new PDFTextStripper();
            String fullText = stripper.getText(document);

            Log.d(TAG, "Extracted PDF text length: " + fullText.length());
            Log.d(TAG, "First 500 chars: " + fullText.substring(0, Math.min(500, fullText.length())));

            return extractStocksFromText(fullText);
        } finally {
            if (document != null) {
                document.close();
            }
        }
    }

    private static List<Stock> extractStocksFromText(String text) {
        Map<String, Stock> stockMap = new LinkedHashMap<>();
        String[] lines = text.split("\\n");

        String currentIsin = null;
        String currentName = null;
        double currentQty = 0;
        double currentAvgPrice = 0;
        boolean lookingForDetails = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            // Check for ISIN on this line
            Matcher isinMatcher = ISIN_PATTERN.matcher(line);
            if (isinMatcher.find()) {
                // Save previous stock if we have one
                if (currentIsin != null && currentQty > 0) {
                    String ticker = deriveTicker(currentName, currentIsin);
                    if (!stockMap.containsKey(ticker)) {
                        stockMap.put(ticker, new Stock(ticker, "N/A", currentQty, currentAvgPrice, "CAS"));
                    }
                }

                currentIsin = isinMatcher.group(1);
                currentQty = 0;
                currentAvgPrice = 0;
                lookingForDetails = true;

                // Extract name: text after the ISIN on the same line, or the preceding text
                String afterIsin = line.substring(isinMatcher.end()).trim();
                // Remove common separators
                afterIsin = afterIsin.replaceAll("^[:\\-–,\\s]+", "").trim();

                if (!afterIsin.isEmpty() && afterIsin.length() > 2) {
                    currentName = cleanCompanyName(afterIsin);
                } else {
                    // Try the text before ISIN
                    String beforeIsin = line.substring(0, isinMatcher.start()).trim();
                    beforeIsin = beforeIsin.replaceAll("[:\\-–,\\s]+$", "").trim();
                    if (!beforeIsin.isEmpty() && beforeIsin.length() > 2) {
                        currentName = cleanCompanyName(beforeIsin);
                    } else {
                        // Look at previous line for company name
                        if (i > 0) {
                            String prevLine = lines[i - 1].trim();
                            if (!prevLine.isEmpty() && !ISIN_PATTERN.matcher(prevLine).find()) {
                                currentName = cleanCompanyName(prevLine);
                            }
                        }
                        if (currentName == null || currentName.isEmpty()) {
                            currentName = currentIsin;
                        }
                    }
                }
                continue;
            }

            // If we're looking for details after an ISIN
            if (lookingForDetails && currentIsin != null) {
                // Check for closing balance / quantity
                Matcher closingMatcher = CLOSING_BAL_PATTERN.matcher(line);
                if (closingMatcher.find()) {
                    currentQty = parseNumber(closingMatcher.group(1));
                }

                Matcher qtyMatcher = QTY_LINE_PATTERN.matcher(line);
                if (qtyMatcher.find() && currentQty == 0) {
                    currentQty = parseNumber(qtyMatcher.group(1));
                }

                // Check for average price
                Matcher priceMatcher = AVG_PRICE_PATTERN.matcher(line);
                if (priceMatcher.find()) {
                    currentAvgPrice = parseNumber(priceMatcher.group(1));
                }

                // NSDL CAS format: numbers in a row like "15.000   0.000   0.000   15.000"
                // The last number is typically the closing balance
                if (currentQty == 0) {
                    Pattern numbersRow = Pattern.compile(
                            "([\\d,]+\\.\\d{3})\\s+[\\d,]+\\.\\d{3}\\s+[\\d,]+\\.\\d{3}\\s+([\\d,]+\\.\\d{3})");
                    Matcher rowMatcher = numbersRow.matcher(line);
                    if (rowMatcher.find()) {
                        currentQty = parseNumber(rowMatcher.group(2));
                    }
                }

                // If we hit the next section or another ISIN block, stop looking
                if (line.startsWith("---") || line.matches("^[A-Z][A-Z ]{10,}$")) {
                    if (currentQty > 0) {
                        String ticker = deriveTicker(currentName, currentIsin);
                        if (!stockMap.containsKey(ticker)) {
                            stockMap.put(ticker, new Stock(ticker, "N/A", currentQty, currentAvgPrice, "CAS"));
                        }
                    }
                    currentIsin = null;
                    currentName = null;
                    currentQty = 0;
                    currentAvgPrice = 0;
                    lookingForDetails = false;
                }
            }
        }

        // Don't forget the last stock
        if (currentIsin != null && currentQty > 0) {
            String ticker = deriveTicker(currentName, currentIsin);
            if (!stockMap.containsKey(ticker)) {
                stockMap.put(ticker, new Stock(ticker, "N/A", currentQty, currentAvgPrice, "CAS"));
            }
        }

        List<Stock> result = new ArrayList<>(stockMap.values());
        Log.d(TAG, "Parsed " + result.size() + " stocks from CAS PDF");
        return result;
    }

    private static String deriveTicker(String companyName, String isin) {
        if (companyName == null || companyName.isEmpty() || companyName.equals(isin)) {
            return isin;
        }

        // Common ISIN to ticker mappings for well-known Indian stocks
        // This handles the most common cases; others use abbreviated company name
        String cleaned = companyName.toUpperCase()
                .replaceAll("\\s*(LIMITED|LTD|INDUSTRIES|CORPORATION|CORP|INDIA|PVT)\\s*\\.?", " ")
                .replaceAll("\\s+", " ")
                .trim();

        // If short enough, use as-is
        if (cleaned.length() <= 12) {
            return cleaned.replaceAll("\\s+", "");
        }

        // Take first word as ticker (e.g., "RELIANCE", "INFOSYS", "TCS")
        String[] words = cleaned.split("\\s+");
        return words[0];
    }

    private static String cleanCompanyName(String name) {
        return name
                .replaceAll("^[\\s:,\\-–]+", "")
                .replaceAll("[\\s:,\\-–]+$", "")
                .replaceAll("\\s{2,}", " ")
                .replaceAll("^Name\\s*:\\s*", "")
                .replaceAll("^Name of Instrument\\s*:\\s*", "")
                .trim();
    }

    private static double parseNumber(String numStr) {
        try {
            return Double.parseDouble(numStr.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
