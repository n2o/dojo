package tddtrainer.gui;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.google.inject.Inject;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import tddtrainer.catalog.Exercise;
import tddtrainer.compiler.AutoCompilerResult;
import tddtrainer.events.ExerciseEvent;
import tddtrainer.events.JavaCodeChangeEvent;
import tddtrainer.events.JavaCodeChangeEvent.CodeType;
import tddtrainer.events.automaton.ResetPhaseEvent;
import tddtrainer.events.automaton.SwitchedToGreenEvent;
import tddtrainer.events.automaton.SwitchedToRedEvent;
import tddtrainer.events.automaton.SwitchedToRefactorEvent;

public class EditorViewController extends SplitPane implements Initializable {

    private JavaCodeArea tests;
    private JavaCodeArea code;

    @FXML
    private TextArea console;
    @FXML
    private TextArea testoutput;

    @FXML
    private AnchorPane codePane;

    @FXML
    private AnchorPane testPane;

    @FXML
    private Label codeLabel;

    @FXML
    private Label testLabel;

    @FXML
    private HBox iGreenBox;

    @FXML
    private Label iRedLabel1;

    @FXML
    private HBox codeBox;

    @FXML
    HBox statuscontainer;

    @FXML
    Label status;

    Logger logger = LoggerFactory.getLogger(EditorViewController.class);

    String revertToCode;
    String revertToTest;
    private final EventBus bus;
    private int fontSize = 15;

    @Inject
    public EditorViewController(FXMLLoader loader, EventBus bus) {
        this.bus = bus;
        bus.register(this);
        URL resource = getClass().getResource("EditorView.fxml");
        loader.setLocation(resource);
        loader.setController(this);
        loader.setRoot(this);
        try {
            loader.load();
        } catch (IOException e) {
            logger.error("Error loading Root view", e);
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        iGreenBox.setVisible(false);
        addEditors();
        AnchorPane.setBottomAnchor(this, 0.0);
        AnchorPane.setLeftAnchor(this, 5.0);
        AnchorPane.setRightAnchor(this, 5.0);
        AnchorPane.setTopAnchor(this, 60.0);

        console.setStyle("-fx-font-family:monospace;");

        subscribeToChangeStream(code, JavaCodeChangeEvent.CodeType.CODE);
        subscribeToChangeStream(tests, JavaCodeChangeEvent.CodeType.TEST);

    }

    private void subscribeToChangeStream(JavaCodeArea area, CodeType type) {
        area.richChanges().filter(ch -> !ch.getInserted().equals(ch.getRemoved())) // XXX
                .successionEnds(Duration.ofMillis(500)).supplyTask(() -> compile(area))
                .awaitLatest(area.richChanges())
                .filterMap(t -> {
                    if (t.isSuccess()) {
                        return Optional.of(t.get());
                    } else {
                        t.getFailure().printStackTrace();
                        return Optional.empty();
                    }
                }).subscribe(t -> bus.post(new JavaCodeChangeEvent(t, type)));
    }

    private Task<String> compile(JavaCodeArea area) {
        Task<String> task = new Task<String>() {
            @Override
            protected String call() throws Exception {
                return area.getText();
            }
        };
        Executors.newSingleThreadExecutor().execute(task);
        return task;
    }

    @Subscribe
    public void showExercise(ExerciseEvent exerciseEvent) {
        Exercise exercise = exerciseEvent.getExercise();
        if (exercise != null) {
            showExercise(exercise);
        }
    }

    public String getCode() {
        return code.getText();
    }

    public String getTest() {
        return tests.getText();
    }

    public void showExercise(Exercise exercise) {
        code.clear();
        code.appendText(exercise.getCode().getCode());
        codeLabel.setText(exercise.getCode().getName());
        tests.clear();
        tests.appendText(exercise.getTest().getCode());
        testLabel.setText(exercise.getTest().getName());
        revertToCode = code.getText();
        revertToTest = tests.getText();
        code.selectRange(0, 0);
        tests.selectRange(0, 0);
    }

    @Subscribe
    private void resetToRed(ResetPhaseEvent event) {
        code.clear();
        code.appendText(revertToCode);
        tests.clear();
        tests.appendText(revertToTest);
        code.getUndoManager().forgetHistory();
        tests.getUndoManager().forgetHistory();
    }

    @Subscribe
    private void changePhaseToRed(SwitchedToRedEvent event) {
        code.disable(true);
        tests.disable(false);
        revertToTest = tests.getText();
        revertToCode = code.getText();
        tests.setStyle("-fx-border-color: crimson;");
        code.setStyle("-fx-border-color: transparent;");
        iGreenBox.setVisible(false);
        iRedLabel1.setText("Write code to make all tests pass");
        AnchorPane.setRightAnchor(codeBox, 15.0);
    }

    @Subscribe
    private void changePhaseToGreen(SwitchedToGreenEvent event) {
        code.disable(false);
        tests.disable(true);
        revertToTest = tests.getText();
        code.setStyle("-fx-border-color: forestgreen;");
        tests.setStyle("-fx-border-color: transparent;");
        iGreenBox.setVisible(true);
        AnchorPane.setRightAnchor(codeBox, iGreenBox.getWidth() + 10);
    }

    @Subscribe
    private void changePhaseToRefactor(SwitchedToRefactorEvent event) {
        code.disable(false);
        tests.disable(false);
        tests.setStyle("-fx-border-color: grey;");
        code.setStyle("-fx-border-color: grey;");
        iGreenBox.setVisible(true);
        iRedLabel1.setText("Modify code, but keep all tests passing");
        AnchorPane.setRightAnchor(codeBox, 15.0);
    }

    private void addEditors() {
        code = new JavaCodeArea();
        code.setEditable(false);
        codePane.getChildren().add(code);
        AnchorPane.setTopAnchor(code, 50.0);
        AnchorPane.setLeftAnchor(code, 20.0);
        AnchorPane.setRightAnchor(code, 20.0);
        AnchorPane.setBottomAnchor(code, 5.0);

        tests = new JavaCodeArea();
        tests.setEditable(false);
        testPane.getChildren().add(tests);
        AnchorPane.setTopAnchor(tests, 50.0);
        AnchorPane.setLeftAnchor(tests, 20.0);
        AnchorPane.setRightAnchor(tests, 20.0);
        AnchorPane.setBottomAnchor(tests, 5.0);
        zoomDefault();
    }

    public void zoomIn() {
        fontSize += 1;
        applyFontSize();
    }

    public void zoomOut() {
        if (fontSize > 8)
            fontSize -= 1;
        applyFontSize();
    }

    public void zoomDefault() {
        fontSize = 13;
        applyFontSize();
    }

    private void applyFontSize() {
        String style = "-fx-font-size:" + fontSize + "px";
        code.setStyle(style);
        tests.setStyle(style);
        console.setStyle(style);
        testoutput.setStyle(style);
    }

    @Subscribe
    public void compileResult(AutoCompilerResult result) {
        console.setText("Compiler Output:\n================\n" + result.getCompilerOutput());
        testoutput.setText("System.out/System.err:\n=================\n" + result.getTestOutput());
        if (result.allClassesCompile()) {
            if (result.allTestsGreen()) {
                status.setText("Code and Test compile, and the tests are passing.");
                status.setStyle("-fx-text-fill: white");
                statuscontainer.setStyle("-fx-background-color: green");
            } else {
                status.setText("Code and Test compile, but the tests are not passing.");
                status.setStyle("-fx-text-fill: white");
                statuscontainer.setStyle("-fx-background-color: red");
            }
        } else {
            status.setText("Code or Test (or both) contain errors.");
            status.setStyle("-fx-text-fill: white");
            statuscontainer.setStyle("-fx-background-color: black");
        }
    }

}
