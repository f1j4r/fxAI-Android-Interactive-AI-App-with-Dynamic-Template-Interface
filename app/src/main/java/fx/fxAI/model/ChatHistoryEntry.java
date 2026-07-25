package fx.fxAI.model;

public class ChatHistoryEntry {
  private long id;
  private String title;
  private String htmlContent;
  private String jsonData;
  private String templateName;
  private String htmlFileName;
  private long timestamp;
  private boolean isTemplateBased;

  // Constructors
  public ChatHistoryEntry(String title, String htmlContent, long timestamp) {
    this.title = title;
    this.htmlContent = htmlContent;
    this.timestamp = timestamp;
    this.isTemplateBased = false;
  }

  public ChatHistoryEntry(String title, String htmlContent, String jsonData,
                          String templateName, String htmlFileName, long timestamp) {
    this.title = title;
    this.htmlContent = htmlContent;
    this.jsonData = jsonData;
    this.templateName = templateName;
    this.htmlFileName = htmlFileName;
    this.timestamp = timestamp;
    this.isTemplateBased = true;
  }

  // Getters
  public long getId() { return id; }
  public String getTitle() { return title; }
  public String getHtmlContent() { return htmlContent; }
  public String getJsonData() { return jsonData; }
  public String getTemplateName() { return templateName; }
  public String getHtmlFileName() { return htmlFileName; }
  public long getTimestamp() { return timestamp; }
  public boolean isTemplateBased() { return isTemplateBased; }

  // Setters
  public void setId(long id) { this.id = id; }
  public void setTitle(String title) { this.title = title; }
  public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }
  public void setJsonData(String jsonData) { this.jsonData = jsonData; }
  public void setTemplateName(String templateName) { this.templateName = templateName; }
  public void setHtmlFileName(String htmlFileName) { this.htmlFileName = htmlFileName; }
  public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
  public void setTemplateBased(boolean templateBased) { this.isTemplateBased = templateBased; }
}
