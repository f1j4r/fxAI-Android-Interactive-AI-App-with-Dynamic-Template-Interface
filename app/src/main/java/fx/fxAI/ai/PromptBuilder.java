package fx.fxAI.ai;

import org.json.JSONObject;
import java.util.Iterator;
import java.util.List;
import fx.fxAI.model.Template;

/**
 * Builds system prompts with token efficiency in mind:
 * - Compact schema representation (instead of raw JSON Schema)
 * - Concise system instructions
 * - Append‑mode hint for array‑based templates (new item only)
 * - Full data context is preserved for accuracy
 * - No template flexibility is lost
 */
public class PromptBuilder {

	// ─── Public API ──────────────────────────────────────────────────────────

	/**
	 * Builds the initial system prompt for selecting a template and generating data.
	 * Includes a compact schema summary and a concrete example.
	 *
	 * @param activeTemplates  List of active templates from DB
	 * @param userMemoryJson   Recent user memory as JSON string (can be "[]" or empty)
	 * @return                 Full system prompt
	 */
	public static String build(List<Template> activeTemplates, String userMemoryJson) {
		StringBuilder sb = new StringBuilder();

		// ── Role & core instruction ──
		sb.append("You generate interactive content for a mobile app. ");
		sb.append("Choose ONE template below and produce a JSON object with ")
				.append("\"selected_template\" and \"data\". ")
				.append("Make sure to choose a template with the most relevant description according to user prompt.\n\n");

		// ── Available templates (now with full schema) ──
		if (activeTemplates.isEmpty()) {
			sb.append("No active templates. Tell the user to enable one in Settings.\n");
		} else {
			sb.append("TEMPLATES:\n");
			for (Template t : activeTemplates) {
				sb.append("  • ").append(t.name).append(" – ").append(t.description).append("\n    Schema: ")
						.append(t.jsonSchema).append("\n\n");
			}

			// ── Memory ──
			if (userMemoryJson != null && !userMemoryJson.isEmpty() && !"[]".equals(userMemoryJson)) {
				sb.append("USER MEMORY (personalise content):\n").append(userMemoryJson).append("\n\n");
			}
		}

		// ── Output rules (strict) ──
		sb.append("RULES:\n");
		sb.append("1. Output ONLY a single JSON object – no markdown, no extra text.\n");
		sb.append("2. If no template fits, use \"selected_template\":\"none\" and a \"message\" in data.\n");
		sb.append("3. The \"data\" object must EXACTLY match the chosen template's JSON Schema.\n");
		sb.append("   - Do not add extra fields.\n");
		sb.append("   - Do not omit required fields.\n");
		sb.append("   - Use the exact types specified (string, number, array, object, etc.).\n");

		return sb.toString();
	}

	/**
	 * Builds a continuation prompt for an ongoing session.
	 * Keeps the full current data for perfect context, but instructs the AI
	 * to return ONLY the new item if the template has a top‑level array.
	 *
	 * @param template        The template being used
	 * @param currentDataJson The FULL current data (JSON string)
	 * @param userInput       The user's latest input (choice, answer, etc.)
	 * @param memoryJson      Recent user memory (can be "[]" or empty)
	 * @return                Continuation system prompt
	 */
	public static String buildContinuation(Template template, String currentDataJson, String userInput,
			String memoryJson) {
		StringBuilder sb = new StringBuilder();

		sb.append("Continue the interactive session from this template.\n");
		sb.append("Template: ").append(template.name).append("\n");
		sb.append("Description: ").append(template.description).append("\n");
		sb.append("Schema: ").append(compactSchema(template.jsonSchema)).append("\n\n");

		// ── Full context (preserved for safety) ──
		sb.append("CURRENT DATA (full state):\n").append(currentDataJson).append("\n\n");

		// ── Memory ──
		if (memoryJson != null && !memoryJson.isEmpty() && !"[]".equals(memoryJson)) {
			sb.append("USER MEMORY:\n").append(memoryJson).append("\n\n");
		}

		// ── Append‑mode hint (only for array‑based templates) ──
		if (isArrayBased(template.jsonSchema)) {
			sb.append("INSTRUCTION: Produce a JSON object according to JSON Schema. ")
					.append("Instead of returning the entire data object, ")
					.append("return **only the new item** to append to the existing array.\n");
			sb.append("Format: {\"newItem\": { /* your new item */ }}\n");
			sb.append("If you prefer to return the full object, that also works – ").append("I will handle both.\n\n");
		} else {
			sb.append("INSTRUCTION: Produce a JSON object according to JSON Schema. ")
					.append("Return a **complete, updated** JSON object ").append("that matches the schema.\n\n");
		}

		sb.append("USER INPUT: \"").append(userInput).append("\"\n");
		sb.append("Respond with JSON only – no extra text.");

		return sb.toString();
	}

	/**
	 * Legacy simple continuation (kept for compatibility, not used by main pipeline).
	 */
	@Deprecated
	public static String buildContinuation(String previousContext, String userMessage) {
		StringBuilder sb = new StringBuilder();
		sb.append("Continue the interactive experience. The previous context was:\n");
		sb.append(previousContext).append("\n\n");
		sb.append("The user's input: ").append(userMessage).append("\n\n");
		sb.append("Respond with the next part in the same JSON format.");
		return sb.toString();
	}

	// ─── Private Helpers ─────────────────────────────────────────────────────

	/**
	 * Converts a raw JSON Schema string into a compact, human‑readable summary.
	 * Example: {"topic":"string", "questions":[{"question":"string"}]}
	 * becomes: {topic: string, questions: array}
	 *
	 * @param jsonSchema  The full schema string from the database
	 * @return            Compact representation (fallbacks to raw string on error)
	 */
	private static String compactSchema(String jsonSchema) {
		if (jsonSchema == null || jsonSchema.isEmpty())
			return "{}";
		try {
			JSONObject schema = new JSONObject(jsonSchema);
			JSONObject props = schema.optJSONObject("properties");
			if (props == null)
				return jsonSchema;

			StringBuilder sb = new StringBuilder("{");
			int count = 0;
			for (Iterator<String> it = props.keys(); it.hasNext();) {
				String key = it.next();
				if (count++ > 0)
					sb.append(", ");
				JSONObject prop = props.getJSONObject(key);
				String type = prop.optString("type", "any");
				if ("array".equals(type)) {
					sb.append(key).append(": array");
				} else if ("object".equals(type)) {
					sb.append(key).append(": object");
				} else {
					sb.append(key).append(": ").append(type);
				}
			}
			sb.append("}");
			return sb.toString();
		} catch (Exception e) {
			// Fallback to the raw schema if parsing fails
			return jsonSchema;
		}
	}

	/**
	 * Checks if the template's schema has at least one top‑level array property.
	 * Used to decide whether to enable the "append‑mode" hint.
	 */
	private static boolean isArrayBased(String jsonSchema) {
		if (jsonSchema == null || jsonSchema.isEmpty())
			return false;
		try {
			JSONObject schema = new JSONObject(jsonSchema);
			JSONObject props = schema.optJSONObject("properties");
			if (props == null)
				return false;
			for (Iterator<String> it = props.keys(); it.hasNext();) {
				String key = it.next();
				JSONObject prop = props.getJSONObject(key);
				if ("array".equals(prop.optString("type"))) {
					return true;
				}
			}
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Finds the name of the first top‑level array property in the schema.
	 * Used by AIBridge when merging new items.
	 */
	public static String findFirstArrayKey(String jsonSchema) {
		if (jsonSchema == null || jsonSchema.isEmpty())
			return null;
		try {
			JSONObject schema = new JSONObject(jsonSchema);
			JSONObject props = schema.optJSONObject("properties");
			if (props == null)
				return null;
			for (Iterator<String> it = props.keys(); it.hasNext();) {
				String key = it.next();
				JSONObject prop = props.getJSONObject(key);
				if ("array".equals(prop.optString("type"))) {
					return key;
				}
			}
			return null;
		} catch (Exception e) {
			return null;
		}
	}
}

