package hpe.clqr.service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
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
import org.apache.pdfbox.io.RandomAccessBuffer;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hslf.HSLFSlideShow;
import org.apache.poi.hslf.model.Slide;
import org.apache.poi.hslf.model.TextRun;
import org.apache.poi.hslf.usermodel.RichTextRun;
import org.apache.poi.hslf.usermodel.SlideShow;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.XSLFSlideShow;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.xmlbeans.XmlException;
import org.junit.Test;
import org.openxmlformats.schemas.drawingml.x2006.main.CTRegularTextRun;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextBody;
import org.openxmlformats.schemas.drawingml.x2006.main.CTTextParagraph;
import org.openxmlformats.schemas.presentationml.x2006.main.CTGroupShape;
import org.openxmlformats.schemas.presentationml.x2006.main.CTShape;
import org.openxmlformats.schemas.presentationml.x2006.main.CTSlide;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
	public int indexBuilder(File indexPath, File dataPath) {
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
	private void subIndexBuilder(IndexWriter fsdWriter, File subPath) {
		File[] fileList = subPath.listFiles();
		for (File file : fileList) {
			if (file.isDirectory()) {
				subIndexBuilder(fsdWriter, file);
			} else /* if (fileType(file.getName())=="txt") */ {
				fileIndexBUilder(fsdWriter, file);
			}
		}
		/*
		 * for (int i = 0; i < subPath.length(); i++) { File file = fileList[i];
		 * if (file.isDirectory()) { subIndexBuilder(fsdWriter, file); } else if
		 * (IsValidType(file.getName())) { fileIndexBUilder(fsdWriter, file); }
		 * }
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

	private void fileIndexBUilder(IndexWriter fsdWriter, File subFile) {
		if (subFile.isHidden() || !subFile.exists() || !subFile.canRead()) {
			return;
		}
		try {
			Directory ramDir = new RAMDirectory();
			Document doc = new Document();
			InputStream in = new FileInputStream(subFile);
			String fileType = fileType(subFile.getName());
			Analyzer analyzer = new StandardAnalyzer(Version.LUCENE_4_9);// 文本分析器
			IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_4_9, analyzer);
			conf.setUseCompoundFile(true);// 采用多文件索引结构,默认为复合索引
			IndexWriter ramWriter = new IndexWriter(ramDir, conf);
			if (fileType != null && !fileType.equals("")) {
				if(fileType.equals("txt")|| fileType.equals("java") || fileType.equals("cs")
						|| fileType.equals("pythonn") || fileType.equals("dtsx") || fileType.equals("sql")) {
					Long fileLengthLong = subFile.length();  
					byte[] fileContent = new byte[fileLengthLong.intValue()]; 
					 in.read(fileContent);  
					 in.close();
					 String context=new String(fileContent);
					Field fieldName = new TextField("name", subFile.getName(), Store.YES);
					doc.add(fieldName);
					Field fieldPath = new TextField("path", subFile.getAbsolutePath(), Store.YES);
					doc.add(fieldPath);
					Field fieldContent = new TextField("content", context, Store.YES);
					doc.add(fieldContent);

					ramWriter.addDocument(doc);// 文档添加到内存索引
					ramWriter.close();// 关闭内存索引，保存添加的数据

					fsdWriter.addIndexes(new Directory[] { ramDir });// 添加内存索引到磁盘索引
				}
				if (fileType.equals("doc") ) {
					// 获取doc的word文档
					WordExtractor wordExtractor = new WordExtractor(in);

					System.out.println("注意：已为文件“" + subFile.getName() + "”创建了索引");
					Field fieldName = new TextField("name", subFile.getName(), Store.YES);
					doc.add(fieldName);
					Field fieldPath = new TextField("path", subFile.getAbsolutePath(), Store.YES);
					doc.add(fieldPath);
					Field fieldContent = new TextField("content", wordExtractor.getText(), Store.YES);
					doc.add(fieldContent);

					ramWriter.addDocument(doc);// 文档添加到内存索引
					ramWriter.close();// 关闭内存索引，保存添加的数据

					fsdWriter.addIndexes(new Directory[] { ramDir });// 添加内存索引到磁盘索引

				} else if (fileType.equals("docx")) {
					XWPFWordExtractor wordExtractor = new XWPFWordExtractor(new XWPFDocument(in));

					System.out.println("注意：已为文件“" + subFile.getName() + "”创建了索引");
					Field fieldName = new TextField("name", subFile.getName(), Store.YES);
					doc.add(fieldName);
					Field fieldPath = new TextField("path", subFile.getAbsolutePath(), Store.YES);
					doc.add(fieldPath);
					Field fieldContent = new TextField("content", wordExtractor.getText(), Store.YES);
					doc.add(fieldContent);

					ramWriter.addDocument(doc);// 文档添加到内存索引
					ramWriter.close();// 关闭内存索引，保存添加的数据

					fsdWriter.addIndexes(new Directory[] { ramDir });// 添加内存索引到磁盘索引

				} else if (fileType.equals("pdf")) {
					PDFParser parser = new PDFParser(new RandomAccessBuffer(in));
					parser.parse();
					PDDocument pdDocument = parser.getPDDocument();
					PDFTextStripper stripper = new PDFTextStripper();
					Field fieldName = new TextField("name", subFile.getName(), Store.YES);
					doc.add(fieldName);
					Field fieldPath = new TextField("path", subFile.getAbsolutePath(), Store.YES);
					doc.add(fieldPath);
					Field fieldContent = new TextField("content", stripper.getText(pdDocument), Store.YES);
					doc.add(fieldContent);

					ramWriter.addDocument(doc);// 文档添加到内存索引
					ramWriter.close();// 关闭内存索引，保存添加的数据

					fsdWriter.addIndexes(new Directory[] { ramDir });// 添加内存索引到磁盘索引
					pdDocument.close();

				} else if (fileType.equals("ppt")) {
					StringBuilder sb = new StringBuilder("");

					SlideShow ppt = new SlideShow(new HSLFSlideShow(in));// path为文件的全路径名称，建立SlideShow
					Slide[] slides = ppt.getSlides();
					for (Slide slide : slides) {
						TextRun[] textRuns = slide.getTextRuns();
						for (TextRun textRun : textRuns) {
							RichTextRun[] richTextRuns = textRun.getRichTextRuns();
							for (int j = 0; j < richTextRuns.length; j++) {
								sb.append(richTextRuns[j].getText());
								sb.append(",");
							}
							sb.append(",");
						}
					}
					Field fieldName = new TextField("name", subFile.getName(), Store.YES);
					doc.add(fieldName);
					Field fieldPath = new TextField("path", subFile.getAbsolutePath(), Store.YES);
					doc.add(fieldPath);
					Field fieldContent = new TextField("content", sb.toString(), Store.YES);
					doc.add(fieldContent);

					ramWriter.addDocument(doc);// 文档添加到内存索引
					ramWriter.close();// 关闭内存索引，保存添加的数据

					fsdWriter.addIndexes(new Directory[] { ramDir });// 添加内存索引到磁盘索引
				} else if (fileType.equals("pptx")) {

					StringBuilder sb = new StringBuilder("");

					XMLSlideShow pptx = new XMLSlideShow(new XSLFSlideShow(subFile.getCanonicalPath()));
					;
					for (XSLFSlide slide : pptx.getSlides()) {
						CTSlide rawSlide = slide._getCTSlide();
						CTGroupShape gs = rawSlide.getCSld().getSpTree();
						CTShape[] shapes = gs.getSpArray();
						for (CTShape shape : shapes) {
							CTTextBody tb = shape.getTxBody();
							if (null == tb)
								continue;
							CTTextParagraph[] paras = tb.getPArray();
							for (CTTextParagraph textParagraph : paras) {
								CTRegularTextRun[] textRuns = textParagraph.getRArray();
								for (CTRegularTextRun textRun : textRuns) {
									sb.append(textRun.getT());
									sb.append(",");
								}
							}
						}

					}
					Field fieldName = new TextField("name", subFile.getName(), Store.YES);
					doc.add(fieldName);
					Field fieldPath = new TextField("path", subFile.getAbsolutePath(), Store.YES);
					doc.add(fieldPath);
					Field fieldContent = new TextField("content", sb.toString(), Store.YES);
					doc.add(fieldContent);

					ramWriter.addDocument(doc);// 文档添加到内存索引
					ramWriter.close();// 关闭内存索引，保存添加的数据

					fsdWriter.addIndexes(new Directory[] { ramDir });// 添加内存索引到磁盘索引
				}
			} else if (fileType.equals("xls") || fileType.equals("xlsx")) {
				Workbook workBook = WorkbookFactory.create(in); // 这种方式 Excel
																// 2003/2007/2010
																// 都是可以处理的
				int sheetCount = workBook.getNumberOfSheets(); // Sheet的数量
				StringBuilder sb = new StringBuilder("");
				// 遍历每个Sheet
				for (int s = 0; s < sheetCount; s++) {
					Sheet sheet = workBook.getSheetAt(s);
					int rowCount = sheet.getPhysicalNumberOfRows(); // 获取总行数
					// 遍历每一行
					for (int r = 0; r < rowCount; r++) {
						Row row = sheet.getRow(r);
						int cellCount = row.getPhysicalNumberOfCells();// 获取总行数
						// 遍历每一列
						for (int c = 0; c < cellCount; c++) {
							Cell cell = row.getCell(c);
							sb.append(cell.getStringCellValue());
							sb.append(",");
						}
					}
				}
				Field fieldName = new TextField("name", subFile.getName(), Store.YES);
				doc.add(fieldName);
				Field fieldPath = new TextField("path", subFile.getAbsolutePath(), Store.YES);
				doc.add(fieldPath);
				Field fieldContent = new TextField("content", sb.toString(), Store.YES);
				doc.add(fieldContent);

				ramWriter.addDocument(doc);// 文档添加到内存索引
				ramWriter.close();// 关闭内存索引，保存添加的数据

				fsdWriter.addIndexes(new Directory[] { ramDir });// 添加内存索引到磁盘索引
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (XmlException e) {
			e.printStackTrace();
		} catch (OpenXML4JException e) {
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
	private String fileType(String fileName) {
		String fileType = fileName.substring(fileName.lastIndexOf(".") + 1, fileName.length()).toLowerCase();
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
