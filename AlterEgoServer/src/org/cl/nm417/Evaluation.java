package org.cl.nm417;

import java.text.DecimalFormat;
import java.util.ArrayList;

import org.cl.nm417.db.Database;
import org.cl.nm417.db.Snippets;
import org.cl.nm417.google.GoogleRerank;
import org.cl.nm417.google.GoogleResult;
import org.cl.nm417.google.GoogleSearch;
import org.cl.nm417.google.GoogleSnippet;

import com.mysql.jdbc.Connection;
import com.mysql.jdbc.PreparedStatement;

public class Evaluation {

	public static GoogleSnippet getNextSnippet(String query, String userid){
		GoogleSnippet next = null;
		Connection conn = Database.openConnection();
		ArrayList<GoogleSnippet> snippets = Snippets.getSnippets(conn, query, userid);
		if (snippets == null || snippets.size() == 0){
			// Do search and write to database
			ArrayList<GoogleResult> results = GoogleSearch.doGoogleSearch(query);
			Snippets.writeSnippets(conn, results, query, userid);
			snippets = Snippets.getSnippets(conn, query, userid);
		} 
		// Check whether some snippets don't have a relevance judgment yet
		ArrayList<GoogleSnippet> toEvaluate = new ArrayList<GoogleSnippet>();
		for (GoogleSnippet snippet: snippets){
			if (snippet.getRelevance() == -1){
				toEvaluate.add(snippet);
			}
		}
		System.out.println(toEvaluate.size() + " to be evaluated");
		// Select the next snippet for relevance judgment
		if (toEvaluate.size() > 0){
			next = toEvaluate.get((int) (Math.random() * (toEvaluate.size() - 1)));
		}
		Database.closeConnection(conn);
		return next;
	}
	
	public static void saveRelevance(String id, String selected){
		if (id != null && selected != null){
			Connection conn = Database.openConnection();
			PreparedStatement stmt;
			try {
				
				stmt = (PreparedStatement) conn.prepareStatement("UPDATE evaluation SET relevance=? WHERE id=?");
				stmt.setInt(1, Integer.parseInt(selected));
				stmt.setInt(2, Integer.parseInt(id));
					
				// Execute the query
				stmt.executeUpdate();
				
				System.out.println("Written relevance of " + selected + " to " + id);
				
			} catch (Exception ex){
				ex.printStackTrace();
			}
			Database.closeConnection(conn);
		}
	}
	
	public static String getRow(String[] fields, boolean bold){
		String html = "<tr>";
		for (String s: fields){
			html += "<td>";
			if (bold){
				html += "<b>";
			}
			html += s;
			if (bold){
				html += "</b>";
			}
			html += "</td>";
		}
		html += "</tr>";
		return html;
	}
	
	private static double getDCG(ArrayList<GoogleSnippet> snippets, ArrayList<GoogleResult> ranking){
		double dcg = 0;
		int i = 0;
		for (GoogleResult res: ranking){
			i++;
			GoogleSnippet snippet = null;
			for (GoogleSnippet snip: snippets){
				if (snip.getGRank() == res.getOriginalRank()){
					snippet = snip;
					res.setNewWeight(snip.getRelevance());
				}
			}
			dcg += (Math.pow(2, snippet.getRelevance()) - 1) / (log2((double)(1.00 + i)));
		}
		return dcg;
	}
	
	public static String calculateDCG(ArrayList<GoogleSnippet> snippets, ArrayList<GoogleResult> ranking, int max, boolean N){
		int i = 0;
		ArrayList<GoogleResult> newres = new ArrayList<GoogleResult>();
		ArrayList<GoogleResult> copy = new ArrayList<GoogleResult>();
		for (GoogleResult res: ranking){
			copy.add(res);
		}
		for (GoogleResult res: copy){
			i++;
			if (max == -1 || i <= max){
				newres.add(res);
			}
		}
		double dcg = getDCG(snippets, newres);
		if (N){
			copy = GoogleRerank.doSort(copy);
			newres = new ArrayList<GoogleResult>();
			i = 0;
			for (GoogleResult res: copy){
				i++;
				if (max == -1 || i <= max){
					newres.add(res);
				}
			}
			double idcg = getDCG(snippets, newres);
			dcg = dcg / idcg;
		}
		DecimalFormat twoDigit = new DecimalFormat("#,##0.000");
		return "" + twoDigit.format(dcg);
	}
	
	public static double log2(double num) {
		return (Math.log(num)/Math.log(2));
	}
	
	public static ArrayList<GoogleSnippet> getSnippets(String query, String userid){
		Connection conn = Database.openConnection();
		ArrayList<GoogleSnippet> snippets = Snippets.getSnippets(conn, query, userid);
		Database.closeConnection(conn);
		return snippets;
	}
	
	public static ArrayList <GoogleResult> getGoogleRanking(String query, String userid){
		ArrayList <GoogleResult> results = new ArrayList <GoogleResult>();
		Connection conn = Database.openConnection();
		ArrayList<GoogleSnippet> snippets = Snippets.getSnippets(conn, query, userid);
		for (GoogleSnippet snip: snippets){
			GoogleResult res = new GoogleResult();
			res.setOriginalRank(snip.getGRank());
			res.setRank(snip.getGRank());
			res.setSummary(snip.getSummary());
			res.setTitle(snip.getTitle());
			res.setUrl(snip.getUrl());
			results.add(res);
		}
		Database.closeConnection(conn);
		return results;
	}
	
}
