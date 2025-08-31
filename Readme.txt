dir /S /B src\*.java > sources.txt

java --module-path %JAVAFX_HOME% --add-modules javafx.controls,javafx.fxml -cp bin com.defender.RansomwareDefender

javac --module-path %JAVAFX_HOME% --add-modules javafx.controls,javafx.fxml -d bin @sources.txt
