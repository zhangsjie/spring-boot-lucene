package hpe.clqr.service;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.Version;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.wltea.analyzer.lucene.IKAnalyzer;

import hpe.clqr.vo.HtmlBean;
import hpe.clqr.vo.HtmlBeanUtil;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;

/**
 * 
 * @author zhangshe
 *
 */

@Service
public class CreateIndex {
	@Value("${fileDirectory.dataDir")
	public static String dataDir;
	// public static String dataDir ="D:/dataDir";
	@Value("${fileDirectory.indexDir")
	public static String indexDir;

	// public static String indexDir ="D:/index";
	/**
	 * 索引创建函数.<br>
	 * 生成IndexWriter创建索引，调用子目录索引函数，并优化存储本地磁盘索引
	 * 
	 * @param indexPath
	 *            指定索引目录
	 * @param dataPath
	 *            待分析目录
	 * @return 返回的文档总数
	 */
	public  int indexBuilder(File indexPath, File dataPath) {
		if (!dataPath.exists() || !dataPath.isDirectory() || !dataPath.canRead()) {
			try {
				throw new IOException(dataPath + " Does not exist or is not allowed access.!");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		int num = 0;
		try {
			Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_4_9);// 文本分析器
			IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_4_9, analyzer);
			conf.setUseCompoundFile(true);// 采用多文件索引结构,默认为复合索引
			Directory fsDir = FSDirectory.open(indexPath);
			IndexWriter fsdWriter = new IndexWriter(fsDir, conf);

			subIndexBuilder(fsdWriter, dataPath);

			num = fsdWriter.numDocs();

			fsdWriter.forceMerge(5);// 优化压缩段,执行优化的方法，参数表示优化称几段索引
			fsdWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		return num;
	}

	/**
	 * 递归函数，递归分析目录.<br>
	 * 如果找到子目录，继续递归；如果找到文件分析文件内容并建立索引
	 * 
	 * @param fsdWriter
	 *            IndexWriter
	 * @param subPath
	 *            待分析目录
	 */
	private  void subIndexBuilder(IndexWriter fsdWriter, File subPath) {
		File[] fileList = subPath.listFiles();
		for(File file:fileList) {
			if (file.isDirectory()) {
				subIndexBuilder(fsdWriter, file);
			} else if (fileType(file.getName())=="txt") {
				txtFileIndexBUilder(fsdWriter, file);
			}
		}
		/*
		 * for (int i = 0; i < subPath.length(); i++) { File file = fileList[i]; if
		 * (file.isDirectory()) { subIndexBuilder(fsdWriter, file); } else if
		 * (IsValidType(file.getName())) { fileIndexBUilder(fsdWriter, file); } }
		 */
	}

	/**
	 * 创建RAM内存索引，生成并添加新文档，且合并到本地磁盘索引中
	 * 
	 * @param fsdWriter
	 *            IndexWriter
	 * @param subFile
	 *            待分析目录
	 */
	private  void txtFileIndexBUilder(IndexWriter fsdWriter, File subFile) {
		if (subFile.isHidden() || !subFile.exists() || !subFile.canRead()) {
			return;
		}
		try {
			Directory ramDir = new RAMDirectory();
			Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_4_9);// 文本分析器
			IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_4_9, analyzer);
			conf.setUseCompoundFile(true);// 采用多文件索引结构,默认为复合索引
			IndexWriter ramWriter = new IndexWriter(ramDir, conf);

			FileReader fileReader = new FileReader(subFile);
			System.out.println("-> 创建索引 : " + subFile.getCanonicalPath());
			Document document = new Document();
			Field fieldName = new TextField("name", subFile.getName(), Store.YES);
			document.add(fieldName);
			Field fieldPath = new TextField("path", subFile.getAbsolutePath(), Store.YES);
			document.add(fieldPath);
			Field fieldContent = new TextField("content", fileReader);
			document.add(fieldContent);

			ramWriter.addDocument(document);// 文档添加到内存索引
			ramWriter.close();// 关闭内存索引，保存添加的数据

			fsdWriter.addIndexes(new Directory[] { ramDir });// 添加内存索引到磁盘索引
			fileReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 判断当前文件名是否符合文件后缀的要求
	 * 
	 * @param name
	 *            文件名
	 * @return true 有效文件
	 */
	private  String  fileType(String fileName) {
		String fileType = fileName.substring(fileName.lastIndexOf(".") + 1,
                fileName.length()).toLowerCase();
		return fileType;
	}



	@Test
	public void search() {
		try {
			Directory dir = FSDirectory.open(new File(CreateIndex.indexDir));
			MultiFieldQueryParser mq = new MultiFieldQueryParser(Version.LUCENE_4_9,
					new String[] { "title", "context" }, new IKAnalyzer());
			Query query = mq.parse("java");
			IndexReader reader = DirectoryReader.open(dir);
			IndexSearcher searcher = new IndexSearcher(reader);
			TopDocs td = searcher.search(query, 10);
			System.out.println(td.totalHits);
			for (ScoreDoc sd : td.scoreDocs) {
				int docId = sd.doc;
				reader.document(docId);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
