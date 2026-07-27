package ssg.pex.converter;

public enum TargetLanguage {

    JAVA("Java", ".java"),
    CSHARP("C#", ".cs"),
    CPP("C++", ".cpp"),
    KOTLIN("Kotlin", ".kt"),
    SCALA("Scala", ".scala"),
    RUBY("Ruby", ".rb"),
    BASIC("BASIC", ".bas"),
    JIT("JIT", ".class");

    private final String displayName;
    private final String fileExtension;

    TargetLanguage(String displayName, String fileExtension) {
        this.displayName = displayName;
        this.fileExtension = fileExtension;
    }

    public String displayName() {
        return displayName;
    }

    public String fileExtension() {
        return fileExtension;
    }
}
