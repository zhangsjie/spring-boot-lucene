package hpe.clqr.controller;

import hpe.clqr.service.CreateIndex;
import hpe.clqr.util.PageUtil;
import hpe.clqr.vo.HtmlBean;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhangshe
 * 
 */
@RestController
public class LuceneController {
	//@Value("${fileDirectory.dataDir}")
	public static String dataDir="C:\\Users\\zhangshe\\eclipse-workspace\\dataDir";
	 
//	@Value("${fileDirectory.indexDir}")
	public static String indexDir="C:\\Users\\zhangshe\\eclipse-workspace\\indexDir";
	@Autowired
	private CreateIndex index;

	@RequestMapping("/")
	public String helloWorld() {
		return "hello world";
	}

	@RequestMapping("/index")
	public String  createIndex() {
		 Directory dir = null;
		File file = new File(indexDir);
		if (file.exists()) {
			file.delete();
			System.out.println("dalete the indexDir");
			file.mkdirs();
		}
		 //  分词器
		 //  写索引的config
		
			int num=index.indexBuilder(new File(indexDir), new File(dataDir));
		
		
		return "共有"+num+" 个文件";
	}

	@RequestMapping("/search")
	public ModelAndView search(String keywords, int num, Model model) throws Exception {
		System.out.println(keywords);
		Directory dir = FSDirectory.open(new File(CreateIndex.indexDir));
		IKAnalyzer analyzer = new IKAnalyzer();
		MultiFieldQueryParser mq = new MultiFieldQueryParser(Version.LUCENE_4_9, new String[] { "title", "context" },
				analyzer);
		Query query = mq.parse(keywords);
		IndexReader reader = DirectoryReader.open(dir);
		IndexSearcher searcher = new IndexSearcher(reader);
		TopDocs td = searcher.search(query, 10 * num);
		ScoreDoc[] scoreDocs = td.scoreDocs;
		System.out.println(td.totalHits);
		int count = td.totalHits;
		PageUtil<HtmlBean> page = new PageUtil<HtmlBean>(num + "", 10 + "", count);
		List<HtmlBean> ls = new ArrayList<HtmlBean>();
		for (int i = (num - 1) * 10; i < Math.min(num * 10, count); i++) {
			ScoreDoc sd = scoreDocs[i];
			int docId = sd.doc;
			Document document = reader.document(docId);
			HtmlBean hb = new HtmlBean();
			SimpleHTMLFormatter formatter = new SimpleHTMLFormatter("<font color=\"red\">", "</font>");
			QueryScorer qs = new QueryScorer(query);
			Highlighter highlighter = new Highlighter(formatter, qs);
			String title = highlighter.getBestFragment(analyzer, "title", document.get("title"));
			String context = highlighter.getBestFragments(analyzer.tokenStream("context", document.get("context")),
					document.get("context"), 3, "...");
			hb.setContext(context);
			hb.setTitle(title);
			hb.setUrl(document.get("url"));
			ls.add(hb);
		}
		page.setList(ls);
		model.addAttribute("page", page);
		model.addAttribute("keywords", keywords);
		return new ModelAndView("search");
	}
}
