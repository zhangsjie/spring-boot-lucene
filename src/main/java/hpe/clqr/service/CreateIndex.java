package hpe.clqr.service;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
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
import org.springframework.stereotype.Service;
import org.wltea.analyzer.lucene.IKAnalyzer;

import hpe.clqr.vo.HtmlBean;
import hpe.clqr.vo.HtmlBeanUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

/**
 * 
 * @author zhangshe
 *
 */

@Service
public class CreateIndex {
    public static String dataDir ="D:/dataDir";
    public static String indexDir ="D:/index";
    @Test
    public void createIndex(){
        Directory dir = null;
        try {
            //  index repository
            dir = FSDirectory.open(new File(indexDir));
            //  分词器
            Analyzer analyzer = new IKAnalyzer();
            //  写索引的config
            IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_4_9,analyzer);
            conf.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            IndexWriter writer = new IndexWriter(dir,conf);
            //  source folder
            Collection<File> files = FileUtils.listFiles(new File(dataDir), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
            RAMDirectory ramDirectory = new RAMDirectory();
            IndexWriterConfig conf1 = new IndexWriterConfig(Version.LUCENE_4_9,analyzer);
            conf1.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            IndexWriter ramWriter = new IndexWriter(ramDirectory,conf1);
            int count=0;
            for (File file :files){

                HtmlBean bean = HtmlBeanUtil.createBean(file);
                Document doc =new Document();
                if(bean==null){
                    continue;
                }
                count++;
                doc.add(new StringField("title",bean.getTitle(), Field.Store.YES));
                doc.add(new StringField("url",bean.getUrl(), Field.Store.YES));
                doc.add(new TextField("context", bean.getContext(), Field.Store.YES));
                ramWriter.addDocument(doc);

                if(count==50){
                    //  50个之后 再把内存上的东西写入到磁盘上， 减少写入磁盘的次数
                    ramWriter.close();
                    writer.addIndexes(ramDirectory);
                    ramDirectory=new RAMDirectory();
                    IndexWriterConfig conf2 = new IndexWriterConfig(Version.LUCENE_4_9,analyzer);
                    conf2.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
                    ramWriter = new IndexWriter(ramDirectory,conf2);
                    count=0;
                }
            }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    @Test
    public void search(){
        try {
            Directory dir = FSDirectory.open(new File(CreateIndex.indexDir));
            MultiFieldQueryParser mq = new MultiFieldQueryParser(Version.LUCENE_4_9,new String[]{"title","context"},new IKAnalyzer());
            Query query = mq.parse("java");
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs td = searcher.search(query, 10);
            System.out.println(td.totalHits);
            for (ScoreDoc sd:td.scoreDocs){
                int docId = sd.doc;
                reader.document(docId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
