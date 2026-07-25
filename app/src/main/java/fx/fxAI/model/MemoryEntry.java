package fx.fxAI.model;

public class MemoryEntry {

  public long id;
  public String topic;
  public int score;
  public String details;
  public long timestamp;

  public MemoryEntry() {
    this.timestamp = System.currentTimeMillis();
  }

  public MemoryEntry(String topic, int score, String details) {
    this.topic = topic;
    this.score = score;
    this.details = details;
    this.timestamp = System.currentTimeMillis();
  }

  @Override
  public String toString() {
    return topic + " - Score: " + score;
  }
}

