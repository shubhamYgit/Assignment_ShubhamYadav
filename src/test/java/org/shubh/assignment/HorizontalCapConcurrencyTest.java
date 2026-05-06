package org.shubh.assignment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.shubh.assignment.api.AddCommentRequest;
import org.shubh.assignment.api.CreatePostRequest;
import org.shubh.assignment.domain.AuthorType;
import org.shubh.assignment.domain.Bot;
import org.shubh.assignment.domain.User;
import org.shubh.assignment.repository.BotRepository;
import org.shubh.assignment.repository.CommentRepository;
import org.shubh.assignment.repository.PostRepository;
import org.shubh.assignment.repository.UserRepository;
import org.shubh.assignment.service.AssignmentService;
import org.shubh.assignment.service.GuardrailRejectedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class HorizontalCapConcurrencyTest {
    private static final int BOT_REQUESTS = 200;
    private static final int HORIZONTAL_CAP = 100;

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private AssignmentService assignmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BotRepository botRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
    }

    @BeforeEach
    void setUp() {
        commentRepository.deleteAll();
        postRepository.deleteAll();
        botRepository.deleteAll();
        userRepository.deleteAll();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @Timeout(60)
    void shouldStopExactlyAtOneHundredBotRepliesUnderConcurrentLoad() throws InterruptedException {
        User user = userRepository.save(new User("spam-target", false));

        List<Bot> bots = new ArrayList<>(BOT_REQUESTS);
        for (int i = 1; i <= BOT_REQUESTS; i++) {
            bots.add(new Bot("Load Bot " + i, "Load test bot " + i));
        }
        bots = botRepository.saveAll(bots);

        Long postId = assignmentService.createPost(
                new CreatePostRequest(user.getId(), AuthorType.USER, "Concurrency test post"))
                .getId();

        ExecutorService executor = Executors.newFixedThreadPool(BOT_REQUESTS);
        CountDownLatch ready = new CountDownLatch(BOT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(BOT_REQUESTS);
        AtomicInteger successfulRequests = new AtomicInteger();
        AtomicInteger rejectedRequests = new AtomicInteger();
        AtomicInteger unexpectedFailures = new AtomicInteger();

        for (Bot bot : bots) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    assignmentService.addComment(
                            postId,
                            new AddCommentRequest(bot.getId(), AuthorType.BOT, null, "Bot reply from " + bot.getName()));
                    successfulRequests.incrementAndGet();
                } catch (GuardrailRejectedException ex) {
                    rejectedRequests.incrementAndGet();
                } catch (Exception ex) {
                    unexpectedFailures.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        assertTrue(ready.await(10, TimeUnit.SECONDS), "Workers did not become ready in time");
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "Workers did not finish in time");

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Executor did not terminate in time");

        assertEquals(HORIZONTAL_CAP, successfulRequests.get(), "Exactly 100 bot comments should be accepted");
        assertEquals(HORIZONTAL_CAP, rejectedRequests.get(), "Exactly 100 bot comments should be rejected");
        assertEquals(0, unexpectedFailures.get(), "No unexpected failures should occur");
        assertEquals(HORIZONTAL_CAP, commentRepository.countByPostId(postId), "PostgreSQL should contain exactly 100 bot comments");
        assertEquals("100", redisTemplate.opsForValue().get("post:" + postId + ":bot_count"), "Redis bot count should be exactly 100");
    }
}
