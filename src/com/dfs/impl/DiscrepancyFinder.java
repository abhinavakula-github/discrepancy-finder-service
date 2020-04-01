package com.dfs.impl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

import org.apache.commons.io.FilenameUtils;

import com.dfs.model.Discrepancy;
import com.dfs.model.DiscrepancyRules;
import com.dfs.model.Rule;
import com.dfs.util.Constants;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public class DiscrepancyFinder {

	public static List<Discrepancy> findDiscrepancy(File file, String findOrRemediateMode, File javaRulrXml,
			String targetLocation) throws IOException {
		List<String> discrepancyLineList = new ArrayList<String>();
		List<String> codeLineList = new ArrayList<String>();
		List<Discrepancy> discrepancyDetailsList = new ArrayList<Discrepancy>();

		try {
			List<String> xmlCodeLineList = new ArrayList<String>();
			Scanner scanner;
			scanner = new Scanner(javaRulrXml);
			while (scanner.hasNextLine()) {
				xmlCodeLineList.add(scanner.nextLine().trim());
			}
			scanner = new Scanner(new File("input-files\\" + file.getName()));
			while (scanner.hasNextLine()) {
				codeLineList.add(scanner.nextLine().trim());
			}
			scanner.close();
			XmlMapper xmlMapper = new XmlMapper();
			StringBuilder xml = new StringBuilder();
			xmlCodeLineList.forEach(xml::append);
			DiscrepancyRules discrepancyRules = xmlMapper.readValue(xml.toString(), DiscrepancyRules.class);
			List<Rule> ruleList = discrepancyRules.getRule();
			List<String> removeDiscrepancyList = new ArrayList<String>();

			if (ruleList.size() > 0) {
				for (int ruleIndex = 0; ruleIndex < ruleList.size(); ruleIndex++) {
					String textPattern = ruleList.get(ruleIndex).getText_pattern() == null ? null
							: ruleList.get(ruleIndex).getText_pattern().getValue().trim();
					if (textPattern != null && ruleList.get(ruleIndex).getFile_pattern().getValue().equalsIgnoreCase(Constants.JAVA_FILE_PATTERN) && codeLineList.contains(textPattern)
							&& ruleList.get(ruleIndex).getRemediation().getAction().trim().equalsIgnoreCase(Constants.ACTION_ENUM.remove.toString())) {
						int discrepancyLineNumber = codeLineList.indexOf(ruleList.get(ruleIndex).getText_pattern().getValue().trim()) + 1;
						discrepancyDetailsList.add(setDiscrepancyData(ruleList, ruleIndex, discrepancyLineNumber, file));
						if (findOrRemediateMode.equalsIgnoreCase("0")) {
							discrepancyLineList.add(ruleList.get(ruleIndex).getText_pattern().getValue().trim() + "  Line number: " + (discrepancyLineNumber));
						} else if (findOrRemediateMode.equalsIgnoreCase("1")) {
							discrepancyLineList.add(ruleList.get(ruleIndex).getText_pattern().getValue().trim() + "  Line number: " + (discrepancyLineNumber));
							/*
							 * javaCodeLineList.remove(ruleList.get(ruleIndex).
							 * getText_pattern().getValue().trim());
							 */
							removeDiscrepancyList.add(ruleList.get(ruleIndex).getText_pattern().getValue().trim());
						}
					} else if (textPattern != null
							&& ruleList.get(ruleIndex).getFile_pattern().getValue().equalsIgnoreCase(Constants.JAVA_FILE_PATTERN)
							&& ruleList.get(ruleIndex).getRemediation().getAction().trim().equalsIgnoreCase(Constants.ACTION_ENUM.replace.toString())) {
						String replaceCondition = ruleList.get(ruleIndex).getRemediation().getCondition().toLowerCase().trim();
						List<Integer> deprecatedLineNumberList = new ArrayList<Integer>();
						for (int lineCounter = 0; lineCounter < codeLineList.size(); lineCounter++) {
							if (codeLineList.get(lineCounter).contains(textPattern))
								deprecatedLineNumberList.add(lineCounter);
						}
						if (findOrRemediateMode.equalsIgnoreCase("0")) {
							for (int lineNumber : deprecatedLineNumberList) {
								discrepancyDetailsList.add(setDiscrepancyData(ruleList, ruleIndex, lineNumber, file));
								discrepancyLineList.add(ruleList.get(ruleIndex).getText_pattern().getValue().trim() + "  Line number: " + (lineNumber + 1));
							}
						} else if (findOrRemediateMode.equalsIgnoreCase("1")) {
							for (int lineNumber : deprecatedLineNumberList) {
								if (codeLineList.get(lineNumber).toLowerCase().trim().contains

								(replaceCondition.toLowerCase())) {
									discrepancyDetailsList.add(setDiscrepancyData(ruleList, ruleIndex, lineNumber,file));
									discrepancyLineList.add(ruleList.get(ruleIndex).getText_pattern().getValue().trim() + "  Line number: " + (lineNumber + 1));
									codeLineList.set(lineNumber, codeLineList.get(lineNumber).replaceAll(textPattern, ruleList.get(ruleIndex).getRemediation().getReplace_with().trim()));
								}
							}
						}
					} else if (ruleList.get(ruleIndex).getFile_pattern().getValue()
							.equalsIgnoreCase(Constants.XML_FILE_PATTERN)) {
						if (ruleList.get(ruleIndex).getFile().contains(file.getName())) {
							discrepancyLineList.add("Deprecated xml file: " + file.getName());
							discrepancyDetailsList.add(setDiscrepancyData(ruleList, ruleIndex, 0, file));
						}
					}
				}
				for (String discrepancy : removeDiscrepancyList)
					codeLineList.removeAll(Collections.singleton(discrepancy));
				if (findOrRemediateMode.equalsIgnoreCase("0")) {
					/* writeDiscrepancyFile(discrepancyLineList, file); */
				} else if (findOrRemediateMode.equalsIgnoreCase("1")) {
					/* writeDiscrepancyFile(discrepancyLineList, file); */
					writeRemidiatedFile(discrepancyLineList, codeLineList, file, targetLocation);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return discrepancyDetailsList;
	}

	public static Discrepancy setDiscrepancyData(List<Rule> ruleList, int ruleIndex, int discrepancyLineNumber,
			File file) {
		Discrepancy discrepancy = new Discrepancy();
		try {
			discrepancy.setFileName(file.getName());
			discrepancy.setFileType(file.getName().substring(file.getName().lastIndexOf('.')));
			discrepancy.setLineNo(discrepancyLineNumber);
			discrepancy.setCategory(
					ruleList.get(ruleIndex).getCategory() == null ? "" : ruleList.get(ruleIndex).getCategory());
			discrepancy.setRuleType(ruleList.get(ruleIndex).getType() == null ? "" : ruleList.get(ruleIndex).getType());
			discrepancy.setPattern(ruleList.get(ruleIndex).getText_pattern() == null ? ""
					: ruleList.get(ruleIndex).getText_pattern().getValue().trim());
			discrepancy.setRecommendation(ruleList.get(ruleIndex).getRemediation() == null ? ""
					: ruleList.get(ruleIndex).getRemediation().getRecommendation());
			discrepancy.setComplexity(ruleList.get(ruleIndex).getComplexity() == null ? ""
					: ruleList.get(ruleIndex).getComplexity().getValue());
			discrepancy.setAutoRemediation("Yes");
			discrepancy.setTimeSavingsInMin(ruleList.get(ruleIndex).getRemediation() == null ? ""
					: ruleList.get(ruleIndex).getRemediation().getSavings());
			/*WriteWorkBook.addInExcel(discrepancy, args);*/
		} catch (Exception e) {
			e.printStackTrace();
		}
		return discrepancy;
	}

	public static void writeDiscrepancyFile(List<Discrepancy> descrepancyDetailsList, String targetLocation) {
		/*
		 * String directory = "discrepancy-output-files"; File dir = new
		 * File(directory); if (!dir.exists()) dir.mkdirs(); Path file =
		 * Paths.get(directory + "\\" + "discrepancy-
		 * " + FilenameUtils.removeExtension(inputFile.getName()) +".txt"); try
		 * { Files.write(file, discrepancyLineList, StandardCharsets.UTF_8); }
		 * catch (IOException e) { e.printStackTrace(); }
		 */

		File dir = new File(targetLocation);
		if (!dir.exists())
			dir.mkdirs();
		Path file = Paths.get(targetLocation + "\\" + "discrepancy-list.txt");
		List<String> discrepancyList = new ArrayList<String>();
		for (Discrepancy discrepancy : descrepancyDetailsList)
			discrepancyList.add(discrepancy.toString());
		try {
			Files.write(file, discrepancyList, StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void writeRemidiatedFile(List<String> discrepancyLineList, List<String> codeLineList, File inputFile,
			String targetLocation) {
		/* String directory = "remidiated-output-files"; */
		File dir = new File(targetLocation);
		if (!dir.exists())
			dir.mkdirs();
		Path file = Paths.get(targetLocation + "\\" + inputFile.getName());
		try {
			if (!inputFile.getName().substring(inputFile.getName().lastIndexOf('.')).equalsIgnoreCase(".xml"))
				Files.write(file, codeLineList, StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void copyFileToApplicationServer(String fileName, String findOrRemediateMode, String sourceLocation) {
		try {
			Path destinationPath = Paths.get("application-server");
			if (!Files.exists(destinationPath, new LinkOption[] { LinkOption.NOFOLLOW_LINKS })) {
				destinationPath = Files.createDirectory(destinationPath);
			}
			destinationPath = Paths.get(destinationPath + "/" + fileName);
			Files.move(Paths.get(sourceLocation + "/" + fileName), destinationPath,
					StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
