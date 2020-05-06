package com.clrvn.controller;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson.JSON;
import com.clrvn.domain.DocumentContent;
import com.clrvn.domain.ParagraphContent;
import com.clrvn.domain.SectionContent;
import com.clrvn.entity.Paper;
import com.clrvn.enums.ResultFailureEnum;
import com.clrvn.exception.PaperException;
import com.clrvn.service.IPaperService;
import com.clrvn.utils.*;
import com.clrvn.vo.ResultVO;
import com.spire.doc.Document;
import com.spire.doc.FileFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * @author Clrvn
 * @description
 * @className PaperController
 */
@Controller
@RequestMapping("/paper")
@Slf4j
public class PaperController {

    @Resource
    private IPaperService paperService;

    @Value("${similarity.paragraph}")
    private Double paragraphSimilarity;
    @Value("${similarity.file}")
    private Double fileSimilarity;

    @PostMapping(value = "test")
    public String testUploadFile(@RequestParam(value = "file") MultipartFile file) throws IOException {
        Document document = new Document(file.getInputStream());
        document.saveToFile(FileUtil.UPLOAD_PATH + "上传" + System.currentTimeMillis() + ".docx", FileFormat.Docx);
        return "yes";
    }

    @GetMapping("/findPaperPage")
    @ResponseBody
    public Page<Paper> findPaperPage(Integer pageIndex, String type, String str) throws IOException {
        log.info("pageIndex={}, type={}, str={}", pageIndex, type, str);
        if (str == null) {
            return paperService.findByOrderByPageViewDesc(pageIndex);
        }
        return paperService.findByEs(pageIndex, type, str);
    }

    /**
     * 处理文件上传，并算出与系统论文的比较
     *
     * @param file 文件
     * @return 成功
     */
    @PostMapping(value = "/uploadPaper")
    public String uploadPaper(@RequestParam("paperFile") MultipartFile file, @RequestParam("author") String author, @RequestParam("keyword") String keyword,
                              @RequestParam("paperName") String paperName, Model model) throws IOException {

        Document uploadDoc = new Document(file.getInputStream());
        DocumentContent uploadContent = new DocumentContent(uploadDoc);
        String fileName = file.getOriginalFilename();

        System.err.println(keyword);
        System.err.println(paperName);
        System.err.println(author);

        if (fileName.endsWith(".doc")) {
            fileName = UIDUtils.getUId() + ".doc";
        } else if (fileName.endsWith(".docx")) {
            fileName = UIDUtils.getUId() + ".docx";
        }

        try {

            //文件上传
            FileUtil.uploadFile(file.getBytes(), FileUtil.UPLOAD_PATH + fileName);

            //添加
            Paper paper = new Paper();
            paper.setAuthor(author);
            paper.setKeyword(keyword);
            paper.setUploadTime(new Date());
            paper.setPageView(0);
            String docContent = uploadContent.getContent();
            paper.setContent(docContent);
            paper.setPaperName(paperName);
            paper.setPaperPath(fileName);

            //比较
            log.debug("================================== 正在比较...==================================  ");
            List<Paper> allPaper = paperService.findAll();

            List<DocumentContent> dataContentList = new ArrayList<>();
            allPaper.forEach(ele -> {
                dataContentList.add(new DocumentContent(new Document(FileUtil.UPLOAD_PATH + ele.getPaperPath())));
            });

            Map<int[], List<String>> result = new HashMap<>();

            List<SectionContent> sectionContents = uploadContent.getSectionContents();
            for (int i = 0, sectionContentsSize = sectionContents.size(); i < sectionContentsSize; i++) {
                SectionContent sectionContent = sectionContents.get(i);
                List<ParagraphContent> paragraphs = sectionContent.getParagraphs();
                for (int j = 0, paragraphsSize = paragraphs.size(); j < paragraphsSize; j++) {
                    ParagraphContent paragraphContent = paragraphs.get(j);
                    String text = paragraphContent.getText();
                    int sectionNum = i;
                    int paragraphNum = j;
                    dataContentList.forEach(dataContent -> {
                        dataContent.getSectionContents().forEach(sectionContent1 -> {
                            sectionContent1.getParagraphs().forEach(paragraph -> {
                                //如果相似度大于配置里面段落的相似度
                                String otherText = paragraph.getText();
                                if (CosineSimilarityUtil.similarity(text, otherText) > paragraphSimilarity) {
                                    int[] key = new int[2];
                                    key[0] = sectionNum;
                                    key[1] = paragraphNum;

                                    List<String> values = result.get(key);

                                    //如果上传的那一行数据还是第一次匹配，所以values为空
                                    if (CollUtil.isEmpty(values)) {
                                        values = new ArrayList<>();
                                        //添加进去
                                        values.add(otherText);
                                        result.put(key, values);
                                    } else {
                                        values.add(otherText);
                                    }
                                }
                            });
                        });
                    });

                }
            }

            System.err.println(JSON.toJSONString(result));
            //重复的字数
            int resultNum = 0;
            //标色
            for (Map.Entry<int[], List<String>> entry : result.entrySet()) {
                int[] key = entry.getKey();
                List<String> text = entry.getValue();
                //这个是其他段的内容，所以不是重复的
                List<String> strings = uploadContent.signText(key[0], key[1], text, Color.yellow);
                System.err.println("重复字数" + strings.stream().mapToInt(String::length).sum());
                resultNum += strings.stream().mapToInt(String::length).sum();
            }
            String returnPath = "报告_" + UIDUtils.getUId() + ".docx";
            uploadContent.getDocument().saveToFile(FileUtil.UPLOAD_PATH + returnPath, FileFormat.Docx);

            paper.setReturnPath(returnPath);
            /*int size = result.keySet().size();
            int paragraphNum = uploadContent.getParagraphNum();
            double ratio = (double) size / paragraphNum;*/

            // 重复的字数 / 文章总字数 = 重复率
            double ratio = (double) resultNum / docContent.length();
            System.out.println("查重率：" + ratio);
            log.debug("==================================  比较完成，查重率： {} ================================== ", ratio);
            //如果查重率没有过，则不保存论文到数据库
            //新增
            if (ratio <= fileSimilarity) {
                paperService.savePaper(paper);
                model.addAttribute("IS_PASS", true);
            }
            model.addAttribute("returnPath", returnPath);
            model.addAttribute("fileRatio", String.format("%.2f", ratio * 100) + "%");

        } catch (Exception e) {
            e.printStackTrace();
            throw new PaperException(ResultFailureEnum.UPLOAD_PAPER_FAILURE);
        }
        return "success";
    }

    @DeleteMapping("/deleteById")
    @ResponseBody
    public ResultVO deleteById(Integer id) {
        try {
            paperService.removePaperById(id);
            return ResultVOUtil.success();
        } catch (Exception ex) {
            throw new PaperException(ResultFailureEnum.REMOVE_PAPER_FAILURE);
        }
    }

    @GetMapping(value = "/viewDocument")
    public ResponseEntity<byte[]> viewDocument(@RequestParam("id") Integer id) {
        try {
            Paper paper = paperService.getOneById(id);

            String path = FileUtil.UPLOAD_PATH + paper.getPaperPath();

            File file = new File(path);
            return ResponseUtils.buildResponseEntity(file, paper.getPaperPath());
        } catch (Exception ex) {
            throw new PaperException(ResultFailureEnum.VIEW_PAPER_FAILURE);
        }
    }

    @GetMapping("/addPageView")
    @ResponseBody
    public ResultVO addPageView(@RequestParam("id") Integer id) {
        //热度加1
        paperService.addPageView(id);
        return ResultVOUtil.success();
    }


    /**
     * 下载报告通过路径
     */
    @GetMapping("/downReturnReport")
    public ResponseEntity<byte[]> downloadReport(@RequestParam("path") String path) throws IOException {
        File file = new File(FileUtil.UPLOAD_PATH + path);
        return ResponseUtils.buildResponseEntity(file, path);
    }

    /**
     * 下载报告
     */
    @GetMapping("/downloadReport")
    public ResponseEntity<byte[]> downloadReport(@RequestParam("id") Integer id) throws IOException {
        Paper paper = paperService.getOneById(id);
        String path = FileUtil.UPLOAD_PATH + paper.getReturnPath();
        File file = new File(path);
        return ResponseUtils.buildResponseEntity(file, paper.getReturnPath());
    }

}
