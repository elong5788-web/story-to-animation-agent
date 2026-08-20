import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 第一步演示:用纯 Java(JDK 自带的 HttpClient)调用 DeepSeek,
 * 把一句故事拆成分镜脚本。不需要任何第三方依赖。
 *
 * 运行前先设置环境变量(密钥不要写死在代码里):
 *   export DEEPSEEK_API_KEY=sk-你的密钥
 *
 * 编译 + 运行(Windows 下要加 -encoding UTF-8,否则中文会乱码):
 *   javac -encoding UTF-8 DeepSeekDemo.java
 *   java DeepSeekDemo "一个女孩在雨天撑伞走过街道"
 */
public class DeepSeekDemo {

    static final String API_URL = "https://api.deepseek.com/chat/completions";
    static final String MODEL = "deepseek-chat";

    public static void main(String[] args) throws Exception {
        String apiKey = System.getenv("DEEPSEEK_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("请先设置环境变量 DEEPSEEK_API_KEY:");
            System.out.println("  export DEEPSEEK_API_KEY=sk-你的密钥");
            return;
        }

        // 故事:优先取命令行参数,否则用默认值
        String story = args.length > 0 ? String.join(" ", args) : "一个女孩在雨天撑伞走过街道";

        // 给大模型的"角色设定":让它扮演动画导演
        String systemPrompt = "你是一个动画导演。请把用户给的故事拆成分镜脚本,"
                + "用 JSON 数组输出。每个镜头包含字段:"
                + "shot(镜头号)、shotType(景别,如远景/中景/特写)、"
                + "description(画面描述)、action(画面动作)。"
                + "只输出 JSON,不要任何解释。";

        String body = """
                {
                  "model": "%s",
                  "messages": [
                    {"role": "system", "content": "%s"},
                    {"role": "user", "content": "%s"}
                  ],
                  "temperature": 0.7
                }
                """.formatted(MODEL, escape(systemPrompt), escape(story));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        System.out.println("正在请求 DeepSeek 拆分镜...\n");
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("HTTP 状态码: " + response.statusCode());
        System.out.println("返回内容:\n" + response.body());
    }

    /** 把字符串转成能安全放进 JSON 的文本(转义反斜杠、双引号、换行等) */
    static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
