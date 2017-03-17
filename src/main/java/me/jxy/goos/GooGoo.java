package me.jxy.goos;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Charsets;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.LineProcessor;
import com.google.common.io.Resources;

/**
 * 生成通用后台需要的模版类
 * 
 * @version 1.0
 * @author foolbeargm@gmail.com
 */
public class GooGoo {

    // schema文件中一个特殊的key，用于自定义操作
    public static final String ACTION_KEY = "singleRecordActions";

    private static Pattern querySchemaPattern = Pattern.compile("^(.*)\\.querySchema\\.js$");
    private static Pattern dataSchemaPattern = Pattern.compile("^(.*)\\.dataSchema\\.js$");

    /**
     * 打印帮助信息
     */
    private static void usage() {
        System.err.println("Param incorrect.");
        System.err.println("Usage: java -jar goos.jar [inputDir] [outputDir]");
    }

    public static void main(String[] args) {
        // 至少要有一个参数
        if (args.length > 2 || args.length < 1) {
            usage();
            return;
        }

        String inputDir = "../src/schema";
        String outputDir = "output";

        if (args.length == 2) {
            inputDir = args[0];
            outputDir = args[1];
        }

        if (args.length == 1) {
            inputDir = args[0];
        }

        // 为了减少jar的大小，不使用logger，直接sysout了
        System.out.println("INFO: input directory = " + inputDir);
        System.out.println("INFO: output directory = " + outputDir);

        File f1 = new File(inputDir), f2 = new File(outputDir);

        if (!f1.exists()) {
            System.err.println("ERROR: " + inputDir + " not exist");
            return;
        }

        if (!f2.exists()) {
            f2.mkdirs();
            System.out.println("INFO: mkdir " + outputDir);
        }

        // 扫描inputDir下的所有文件
        Set<String> allTable = Sets.newHashSet();
        Matcher m1, m2;
        for (File f : f1.listFiles()) {
            String fileName = f.getName();
            String tableName = null;
            m1 = querySchemaPattern.matcher(fileName);
            if (m1.matches()) {
                tableName = m1.group(1);
                generateQueryVO(f, outputDir, tableName);
            }

            m2 = dataSchemaPattern.matcher(fileName);
            if (m2.matches()) {
                tableName = m2.group(1);
                generateVO(f, outputDir, tableName);
            }

            if (tableName != null && !allTable.contains(tableName)) {
                generateController(outputDir, tableName);
                allTable.add(tableName);
            }
        }

        // 每个表特定的类生成完毕，开始生成一些通用的类
        try {
            // 直接copy过去就好，不用render了
            copyFileFromClasspath("LoginController.sample", outputDir, "LoginController.java");
            copyFileFromClasspath("CommonResult.sample", outputDir, "CommonResult.java");
            copyFileFromClasspath("UploadController.sample", outputDir, "UploadController.java");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return;
    }

    /**
     * 根据QuerySchema生成QueryVO
     */
    private static void generateQueryVO(File schemaFile, String outputDir, String tableName) {
        System.out.println("INFO: generating QueryVO for " + tableName);

        try {
            // 这个render的过程借鉴了一些velocity的理念
            Map<String, String> params = getParamMap(tableName);
            JSONArray schema = parseJson(schemaFile);

            // 开始处理schema，根据dataType和showType生成各个字段
            StringBuilder fields = new StringBuilder();
            for (int i = 0; i < schema.size(); i++) {
                // 一个字段处理失败不应该影响全局
                try {
                    JSONObject field = schema.getJSONObject(i);
                    String dataType = Objects.firstNonNull(field.getString("dataType"), "varchar");
                    String showType = Objects.firstNonNull(field.getString("showType"), "normal");

                    if ("between".equals(showType)) {
                        fields.append(getBetweenField(dataType, field.getString("key")));
                    } else if ("checkbox".equals(showType) || "multiSelect".equals(showType)) {
                        fields.append(getListField(dataType, field.getString("key")));
                    } else {
                        fields.append(getSingleField(dataType, field.getString("key")));
                    }
                } catch (Exception e) {
                    System.err.println("ERROR: parsing field " + schema.getJSONObject(i) + ": " + e.getMessage());
                }
            }

            // 读取模版文件并做变量替换
            params.put("fields", fields.toString());
            List<String> lines = renderTemplate("QueryVO.sample", params);

            // 将parse后的内容写入文件
            writeLinesToFile(lines, outputDir, tableName, "QueryVO.java");
        } catch (Exception e) {
            System.err.println("ERROR: generating QueryVO ERROR: " + schemaFile.getAbsolutePath());
            e.printStackTrace();
        }
    }

    /**
     * 根据dataSchema生成VO
     */
    private static void generateVO(File schemaFile, String outputDir, String tableName) {
        System.out.println("INFO: generating VO for " + tableName);

        try {
            Map<String, String> params = getParamMap(tableName);
            JSONArray schema = parseJson(schemaFile);

            // 开始处理schema
            StringBuilder fields = new StringBuilder();
            for (int i = 0; i < schema.size(); i++) {
                try {
                    JSONObject field = schema.getJSONObject(i);
                    if (ACTION_KEY.equals(field.getString("key"))) {
                        System.out.println("INFO: skip field " + ACTION_KEY);
                        continue;
                    }

                    String dataType = Objects.firstNonNull(field.getString("dataType"), "varchar");
                    String showType = Objects.firstNonNull(field.getString("showType"), "normal");

                    // 对于file和image要特殊处理下
                    if ("file".equals(showType) || "image".equals(showType)) {
                        if (!field.containsKey("max") || field.getInteger("max") == 1) {
                            fields.append(getSingleField(dataType, field.getString("key")));
                        } else {
                            fields.append(getListField(dataType, field.getString("key")));
                        }
                        continue;
                    }

                    if ("checkbox".equals(showType) || "multiSelect".equals(showType) || "imageArray".equals(showType)) {
                        fields.append(getListField(dataType, field.getString("key")));
                    } else {
                        fields.append(getSingleField(dataType, field.getString("key")));
                    }
                } catch (Exception e) {
                    System.err.println("ERROR: parsing field " + schema.getJSONObject(i) + ": " + e.getMessage());
                }
            }

            // 读取模版文件并做变量替换
            params.put("fields", fields.toString());
            List<String> lines = renderTemplate("VO.sample", params);

            // 将parse后的内容写入文件
            writeLinesToFile(lines, outputDir, tableName, "VO.java");
        } catch (Exception e) {
            System.err.println("ERROR: generating VO ERROR: " + schemaFile.getAbsolutePath());
            e.printStackTrace();
        }
    }

    /**
     * 生成controller
     */
    private static void generateController(String outputDir, String tableName) {
        System.out.println("INFO: generating Controller for " + tableName);

        try {
            Map<String, String> params = getParamMap(tableName);
            List<String> lines = renderTemplate("Controller.sample", params);
            writeLinesToFile(lines, outputDir, tableName, "Controller.java");
        } catch (Exception e) {
            System.err.println("ERROR: generating Controller ERROR: " + tableName);
            e.printStackTrace();
        }
    }

    /**获得生成某个表的java文件时，render所需的参数*/
    private static Map<String, String> getParamMap(String tableName) {
        String lowCamelName = tableName;
        String upCamelName = tableName.substring(0, 1).toUpperCase() + tableName.substring(1);
        Map<String, String> params = Maps.newHashMap();
        params.put("lowCamelName", lowCamelName);
        params.put("upCamelName", upCamelName);
        return params;
    }

    /**生成普通的字段*/
    private static String getSingleField(String dataType, String key) {
        if ("int".equals(dataType)) {
            return "private Long " + key + ";\n";
        } else if ("float".equals(dataType)) {
            return "private Double " + key + ";\n";
        } else if ("varchar".equals(dataType)) {
            return "private String " + key + ";\n";
        } else if ("datetime".equals(dataType)) {
            return "private Date " + key + ";\n";
        } else {
            throw new RuntimeException("unknown dataType " + dataType);
        }
    }

    /**生成list字段*/
    private static String getListField(String dataType, String key) {
        if ("int".equals(dataType)) {
            return "private List<Long> " + key + ";\n";
        } else if ("varchar".equals(dataType)) {
            return "private List<String> " + key + ";\n";
        } else if ("float".equals(dataType)) {
            return "private List<Double> " + key + ";\n";
        } else if ("datetime".equals(dataType)) {
            return "private List<Date> " + key + ";\n";
        } else {
            throw new RuntimeException("unknown dataType " + dataType);
        }
    }

    /**生成两个字段，用于范围查询，只有int/float/datetime可能出现范围查询*/
    private static String getBetweenField(String dataType, String key) {
        StringBuilder sb = new StringBuilder();
        if ("int".equals(dataType)) {
            sb.append("private Long " + key + "Begin;\n");
            sb.append("private Long " + key + "End;\n");
        } else if ("float".equals(dataType)) {
            sb.append("private Double " + key + "Begin;\n");
            sb.append("private Double " + key + "End;\n");
        } else if ("datetime".equals(dataType)) {
            sb.append("private Date " + key + "Begin;\n");
            sb.append("private Date " + key + "End;\n");
        } else {
            throw new RuntimeException("unknown dataType " + dataType);
        }

        return sb.toString();
    }

    /**读取schema文件并转换为json对象*/
    private static JSONArray parseJson(File schemaFile) throws IOException {

        // 这个方法其实有很多问题，可能有各种corner case，要将js的对象转换为java中的json还是有点麻烦的
        // 难道要写一个语法解析器么。。。太麻烦了
        // 先将就着用吧，这个方法不会校验schema的正确性，对json格式也有要求（以webstorm的默认的格式化为准），要注意下
        // 如果json格式不符合要求，即使是正确的json，解析也会出错

        Stack<String> stack = new Stack<String>();
        BufferedReader br = new BufferedReader(new FileReader(schemaFile));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0)
                continue;
            if (line.startsWith("//")) // 忽略注释
                continue;
            if (line.startsWith("import")) // 开始时的import语句
                continue;
            if (line.startsWith("module.exports")) // module语句
                continue;

            // 找到第一个json开始的地方
            if (stack.size() == 0 && !line.equals("{")) {
                continue;
            }

            stack.push(line);

            if (line.equals("},")) { // 碰到了json的结尾
                List<String> list = Lists.newArrayList(); // 暂存json中间的行
                while (stack.size() > 0) {
                    String lastLine = stack.pop(); // pop直到碰到{
                    if (lastLine.equals("{")) {
                        break;
                    }
                    list.add(lastLine);
                }
                // 如果stack.size=0了，说明已经是最外层的json了
                if (stack.size() == 0) {
                    sb.append("}{");
                    appendLine(sb, list);
                }
            }
        }
        br.close();

        // 组合最终的json字符串
        String tmp = "[" + sb.substring(1) + "}]";

        // {a:1, b:2, c:3,}这种结构，在js里能正常识别为一个对象，但fastjson会出错
        char[] charArray = tmp.toCharArray();
        for (int i = 0; i < charArray.length - 1; i++) {
            char thisChar = charArray[i];
            char nextChar = charArray[i + 1];
            if (thisChar == ',' && (nextChar == '}' || nextChar == ']')) {
                charArray[i] = ' ';
            }
        }
        return JSONArray.parseArray(new String(charArray));
    }

    // 转换json时的辅助方法
    private static void appendLine(StringBuilder sb, List<String> list) {
        for (String line : list) {
            // 只关心特定的字段
            if (!line.startsWith("key:") && !line.startsWith("dataType:") && !line.startsWith("showType:") && !line.startsWith("max:")) {
                continue;
            }
            if (line.contains("//")) {// inline注释
                sb.append(line.substring(0, line.indexOf("//")));
            } else {
                sb.append(line);
            }
        }
    }

    /*
     * 
     * 下面开始是一些辅助方法
     * 
     */

    /**
     * 从classpath中读取某个文件，原样写入outputDir
     */
    private static void copyFileFromClasspath(String inputFile, String outputDir, String outputName) throws IOException {
        File target = new File(outputDir, outputName);
        if (target.exists()) {
            System.out.println("INFO: delete exist file " + target.getAbsolutePath());
            target.delete();
        }
        System.out.println("INFO: writing file " + target.getAbsolutePath());
        BufferedWriter bw = Files.newBufferedWriter(target.toPath());
        for (String line : Resources.readLines(Resources.getResource(inputFile), Charsets.UTF_8)) {
            bw.write(line);
            bw.newLine();
        }
        bw.close();
    }

    /**
     * 将parse好的行写入一个文件
     */
    private static void writeLinesToFile(List<String> lines, String outputDir, String tableName, String fileName) throws IOException {
        ensureDir(outputDir, tableName);
        String upCamelName = tableName.substring(0, 1).toUpperCase() + tableName.substring(1);
        File target = new File(outputDir + "/" + tableName + "/" + upCamelName + fileName);
        if (target.exists()) {
            System.out.println("INFO: delete exist file " + target.getAbsolutePath());
            target.delete();
        }
        System.out.println("INFO: writing file " + target.getAbsolutePath());
        BufferedWriter bw = Files.newBufferedWriter(target.toPath());
        for (String line : lines) {
            bw.write(line);
            bw.newLine();
        }
        bw.close();
    }

    /**
     * 读取模版文件并做变量替换
     */
    private static List<String> renderTemplate(String templateFileName, final Map<String, String> params) throws MalformedURLException, IOException {
        final Pattern p = Pattern.compile("\\{(.*?)\\}");
        // 定义每行的处理逻辑
        LineProcessor<List<String>> processor = new LineProcessor<List<String>>() {

            private List<String> result = Lists.newArrayList();

            @Override
            public boolean processLine(String line) throws IOException {
                String tmp = line;
                Matcher m = p.matcher(line);
                while (m.find()) {
                    String key = m.group(1);
                    if (params.containsKey(key)) {
                        tmp = tmp.replaceAll("\\{" + key + "\\}", params.get(key));
                    }
                }

                result.add(tmp);
                return true;
            }

            @Override
            public List<String> getResult() {
                return result;
            }
        };

        return Resources.readLines(Resources.getResource(templateFileName), Charsets.UTF_8, processor);
    }

    /**
     * 确保某个目录存在，不存在则新建
     */
    private static void ensureDir(String parent, String child) {
        File f = new File(parent, child);
        if (!f.exists())
            f.mkdirs();
    }
}
