package de.frauas.girokonto.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Bei {@code Accept-Language: en} werden Texte in JSON-Antworten übersetzt: Meldungen, Titel,
 * Bezeichnungen (…Label), Begründungen usw. Codes, Beträge und Nutzerdaten bleiben unverändert,
 * soweit sie nicht in der Übersetzungstabelle stehen.
 */
@RestControllerAdvice
public class EnglishResponseAdvice implements ResponseBodyAdvice<Object> {

    static final Set<String> FIELDS = Set.of("message", "title", "body", "label", "text", "reason", "description",
            "counterpartyName", "name", "format", "errors", "tips", "permissions", "bankName", "accountName",
            "requirements", "conditions", "notes", "note", "institution", "explanation", "disclaimer", "warnings", "assumptions");
    private static final String[] SUFFIXES = {"Label", "Reason", "Explanation", "Message", "Text", "Description"};

    private final ServerTexts texts;
    private final ObjectMapper mapper;

    public EnglishResponseAdvice(ServerTexts texts, ObjectMapper mapper) {
        this.texts = texts;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return AbstractJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body == null || !ServerTexts.wantsEnglish(request.getHeaders().getFirst("Accept-Language"))) {
            return body;
        }
        JsonNode tree = mapper.valueToTree(body);
        return translate(tree, false);
    }

    static boolean translatable(String field) {
        if (field == null) {
            return false;
        }
        if (FIELDS.contains(field)) {
            return true;
        }
        for (String s : SUFFIXES) {
            if (field.endsWith(s)) {
                return true;
            }
        }
        return false;
    }

    JsonNode translate(JsonNode node, boolean all) {
        if (node.isTextual()) {
            return all ? TextNode.valueOf(texts.toEnglish(node.asText())) : node;
        }
        if (node.isArray()) {
            ArrayNode a = (ArrayNode) node;
            for (int i = 0; i < a.size(); i++) {
                a.set(i, translate(a.get(i), all));
            }
            return a;
        }
        if (node.isObject()) {
            ObjectNode o = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> it = o.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                boolean t = all || translatable(e.getKey()) || "fieldErrors".equals(e.getKey());
                e.setValue(translate(e.getValue(), t && (e.getValue().isTextual() || e.getValue().isArray()
                        || "fieldErrors".equals(e.getKey()))));
            }
            return o;
        }
        return node;
    }
}
