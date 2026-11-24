package com.wlf.ai.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wlf.ai.chat.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 评测数据导出：用真实链路逐题作答，产出 RAGAS 输入 JSONL。 */
@Slf4j
@Component
@Profile("eval")
public class EvalRunner implements ApplicationRunner {

    /** 评测不涉及用户数据，给一个固定 id 即可 */
    private static final Long EVAL_USER_ID = 0L;

    private final ChatService chatService;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext context;
    private final Path questionsPath;
    private final Path outputPath;

    public EvalRunner(ChatService chatService,
                      ObjectMapper objectMapper,
                      ConfigurableApplicationContext context,
                      @Value("${jotang.ai.eval.questions:../eval/questions.jsonl}") String questionsPath,
                      @Value("${jotang.ai.eval.output:../eval/out/run.jsonl}") String outputPath) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
        this.context = context;
        Path questions = Path.of(questionsPath);
        Path base = baseDirOf(questions);
        this.questionsPath = base.resolve(questions).normalize();
        this.outputPath = base.resolve(Path.of(outputPath)).normalize();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        log.info("评测输入 {}，输出 {}", questionsPath, outputPath);
        List<EvalQuestion> questions = readQuestions();
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        int succeeded = 0;
        int failed = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (int i = 0; i < questions.size(); i++) {
                EvalQuestion question = questions.get(i);
                try {
                    ChatService.EvalAnswer answer = chatService.answerForEval(EVAL_USER_ID, question.question());
                    writer.write(objectMapper.writeValueAsString(new RagasRow(
                            question.question(),
                            answer.contexts().stream().map(hit -> hit.text()).toList(),
                            answer.answer(),
                            question.reference())));
                    writer.newLine();
                    succeeded++;
                    log.info("评测进度 {}/{}：{}", i + 1, questions.size(), question.id());
                } catch (RuntimeException e) {
                    failed++;
                    log.error("评测失败：id={}，question=[{}]", question.id(), question.question(), e);
                }
            }
        }

        log.info("评测导出完成：共 {} 题，成功 {}，失败 {}；输出 {}",
                questions.size(), succeeded, failed, outputPath.toAbsolutePath());
        int exitCode = failed == 0 ? 0 : 1;
        System.exit(SpringApplication.exit(context, () -> exitCode));
    }

    /** 相对路径在 IDEA 工作目录、模块目录、仓库根下都可能成立，用 questions.jsonl 实际存在与否来判定。 */
    private static Path baseDirOf(Path questions) {
        if (questions.isAbsolute()) {
            return Path.of("");
        }

        List<Path> bases = new ArrayList<>();
        Path cwd = Path.of("").toAbsolutePath();
        bases.add(cwd);
        if (cwd.getParent() != null) {
            bases.add(cwd.getParent());
        }
        try {
            Path location = Path.of(EvalRunner.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            for (Path dir = location; dir != null; dir = dir.getParent()) {
                bases.add(dir);
            }
        } catch (Exception e) {
            log.debug("无法从 class 位置推断评测目录", e);
        }

        for (Path base : bases) {
            if (Files.exists(base.resolve(questions))) {
                return base;
            }
        }
        return cwd;
    }

    private List<EvalQuestion> readQuestions() throws IOException {
        List<EvalQuestion> questions = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(questionsPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) {
                    continue;
                }
                EvalQuestion question = objectMapper.readValue(line, EvalQuestion.class);
                if (question.question() == null || question.question().isBlank()) {
                    throw new IOException("第 " + lineNo + " 行缺少 question：" + questionsPath.toAbsolutePath());
                }
                questions.add(question);
            }
        }
        return questions;
    }

    /** 评测集一题；question 与 reference 由人工维护。 */
    private record EvalQuestion(String id, String question, String reference) {
    }

    /** RAGAS 单轮样本字段；不要加额外字段，ragas 按关键字构造样本。 */
    private record RagasRow(
            @JsonProperty("user_input") String userInput,
            @JsonProperty("retrieved_contexts") List<String> retrievedContexts,
            String response,
            String reference) {
    }
}
