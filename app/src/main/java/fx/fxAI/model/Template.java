package fx.fxAI.model;

public class Template {

  public long id;
  public String name;
  public String description;
  public String htmlFileName;
  public String jsonSchema;
  public boolean isDefault;
  public boolean isActive;

  public Template() {
    this.isActive = true;
    this.isDefault = false;
  }

  public Template(String name, String description, String htmlFileName, String jsonSchema) {
    this.name = name;
    this.description = description;
    this.htmlFileName = htmlFileName;
    this.jsonSchema = jsonSchema;
    this.isActive = true;
    this.isDefault = false;
  }

  @Override
  public String toString() {
    return name + " (" + (isActive ? "Active" : "Inactive") + ")";
  }
}

