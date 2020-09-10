package morph;

import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import edu.northwestern.at.morphadorner.MorphAdorner;
import com.mongodb.client.FindIterable; 
import com.mongodb.client.MongoDatabase; 
import com.mongodb.client.MongoCollection; 
import com.mongodb.client.model.Filters;
import static com.mongodb.client.model.Filters.eq;
import org.bson.Document;

import morph.MongoConnection;
import morph.StringAdorn;

public class AdornDocs 
{
    public void adorn_documents(StringAdorn adorner, 
	    ArrayList<Document> docs, 
	    MongoDatabase db) {

	MongoCollection<Document> lemmacol = db.getCollection("docs.lemma");
	MongoCollection<Document> poscol = db.getCollection("docs.pos");
	MongoCollection<Document> stdcol = db.getCollection("docs.std");
	MongoCollection<Document> truncatedcol = db.getCollection("docs.truncated");

	docs.forEach((temp) -> {
	    int id = (int)temp.get("_id");

	    FindIterable<Document> l = lemmacol.find(Filters.eq("_id", id));
	    FindIterable<Document> p = lemmacol.find(Filters.eq("_id", id));
	    FindIterable<Document> s = lemmacol.find(Filters.eq("_id", id));
	    FindIterable<Document> t = lemmacol.find(Filters.eq("_id", id));

	    if (l != null && p != null && s != null && t != null) {
		return; // everything in the foreach is a lambda method so this works as a continue statement
	    }

	    System.out.println("Processing doc " + String.valueOf(id));
	    String text = temp.get("text").toString();

	    try {
		ArrayList<String[]> result = adorner.adorn_string(text); 

		// this is a list of string arrays
		// each entry is the 4 forms of the word:
		// original, std, lemma, pos

		ArrayList<String> original = new ArrayList<String>();
		ArrayList<String> std = new ArrayList<String>();
		ArrayList<String> lemma = new ArrayList<String>();
		ArrayList<String> pos = new ArrayList<String>();

		//System.out.println("-------------------------------");

		for (int i = 0; i < result.size(); i++) {
		    original.add(result.get(i)[0]);
		    std.add(result.get(i)[1]);
		    lemma.add(result.get(i)[2]);
		    pos.add(result.get(i)[3]);

		}


		// write to docs.lemma
		Document ld = new Document();
		ld.append("lemma", String.join("\t", lemma));
		ld.append("_id", id);
		lemmacol.insertOne(ld);

		// write to docs.pos
		Document pd = new Document();
		pd.append("pos", String.join("\t", pos));
		pd.append("_id", id);
		poscol.insertOne(pd);

		// write to docs.std
		Document sd = new Document();
		sd.append("std", String.join("\t", std));
		sd.append("_id", id);
		stdcol.insertOne(sd);

		// add fields to docs.truncated (std, lemma, pos)
		int index = Math.min(500, original.size());
		Document td = new Document();
		td.append("_id", id);
		td.append("lemma", lemma.subList(0, index));
		td.append("pos", pos.subList(0, index));
		td.append("std", std.subList(0, index));
		td.append("original", original.subList(0, index));
		truncatedcol.insertOne(td);
	    }
	    catch (Exception e) {
		System.out.println(e.getMessage());
	    }
	});
    }

    public static void main( String[] args)
    {
	boolean overwrite = false;

	if (args[0] == "--overwrite") {
	    overwrite = true;
	}

	long heapMaxSize = Runtime.getRuntime().maxMemory() / 1000000;
	System.out.println(heapMaxSize);

	System.out.println("connecting to database");
	MongoConnection con = new MongoConnection();
	MongoDatabase db = con.connect_to_db("../mongo-credentials.json");
	System.out.println("reading data from database");
	ArrayList<Document> docs = con.read_from_db(db, "docs.text");

	System.out.println("initializing adorner");
	AdornDocs runner = new AdornDocs();
	StringAdorn adorner = new StringAdorn();

	System.out.println("Adorning documents");

	if (overwrite) {
	    MongoCollection<Document> lemmacol = db.getCollection("docs.lemma");
	    MongoCollection<Document> poscol = db.getCollection("docs.pos");
	    MongoCollection<Document> stdcol = db.getCollection("docs.std");
	    MongoCollection<Document> truncatedcol = db.getCollection("docs.truncated");
	    lemmacol.drop();
	    poscol.drop();
	    stdcol.drop();
	    truncatedcol.drop();
	}

	runner.adorn_documents(adorner, docs, db);
    }// main
}
