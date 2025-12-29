// Contenu pour QuizActivity.java
package com.example.signlanguagetranslator;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuizActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView questionCounterText;
    private ImageView questionImage;
    private Button option1Button, option2Button, option3Button, option4Button;
    private ProgressBar progressBar;

    private List<QuestionModel> questionList = new ArrayList<>();
    private int currentQuestionIndex = 0;
    private int score = 0;
    private String categoryId;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_quiz);

        // Récupération de l'ID de la catégorie
        categoryId = getIntent().getStringExtra("CATEGORY_ID");
        if (categoryId == null) {
            Toast.makeText(this, "Catégorie non trouvée", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialisation des vues
        questionCounterText = findViewById(R.id.question_counter_text);
        questionImage = findViewById(R.id.question_image);
        option1Button = findViewById(R.id.option1_button);
        option2Button = findViewById(R.id.option2_button);
        option3Button = findViewById(R.id.option3_button);
        option4Button = findViewById(R.id.option4_button);
        progressBar = findViewById(R.id.quiz_progress_bar);

        // Listeners pour les boutons
        option1Button.setOnClickListener(this);
        option2Button.setOnClickListener(this);
        option3Button.setOnClickListener(this);
        option4Button.setOnClickListener(this);

        // Connexion à Firebase
        db = FirebaseFirestore.getInstance();
        fetchQuestions();
    }

    private void fetchQuestions() {
        showLoading(true);
        db.collection("quizzes").document(categoryId).collection("questionnes")
                .get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        questionList.clear();
                        for (QueryDocumentSnapshot document : task.getResult()) {
                            questionList.add(document.toObject(QuestionModel.class));
                        }
                        // Mélanger les questions pour un quiz différent à chaque fois
                        Collections.shuffle(questionList);
                        startQuiz();
                    } else {
                        showLoading(false);
                        Toast.makeText(QuizActivity.this, "Erreur de chargement des questions.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startQuiz() {
        if (questionList.isEmpty()) {
            Toast.makeText(this, "Aucune question disponible.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        showLoading(false);
        currentQuestionIndex = 0;
        score = 0;
        displayQuestion();
    }

    private void displayQuestion() {
        if (currentQuestionIndex >= questionList.size()) {
            // Le quiz est terminé
            showResults();
            return;
        }

        resetButtonColors();
        enableButtons(true);
        QuestionModel currentQuestion = questionList.get(currentQuestionIndex);

        questionCounterText.setText("Question " + (currentQuestionIndex + 1) + "/" + questionList.size());

        // Charger l'image depuis l'URL GitHub
        Glide.with(this)
                .load(currentQuestion.getQuestionImageUrl())
                .into(questionImage);

        // Afficher les options
        option1Button.setText(currentQuestion.getOption1());
        option2Button.setText(currentQuestion.getOption2());
        option3Button.setText(currentQuestion.getOption3());
        option4Button.setText(currentQuestion.getOption4());
    }

    @Override
    public void onClick(View v) {
        enableButtons(false); // Désactiver les boutons après une réponse
        Button clickedButton = (Button) v;
        String selectedAnswer = clickedButton.getText().toString();
        String correctAnswer = questionList.get(currentQuestionIndex).getCorrectAnswer();

        if (selectedAnswer.equals(correctAnswer)) {
            score++;
            clickedButton.setBackgroundColor(Color.GREEN);
        } else {
            clickedButton.setBackgroundColor(Color.RED);
            // Mettre en vert la bonne réponse
            if (option1Button.getText().toString().equals(correctAnswer)) option1Button.setBackgroundColor(Color.GREEN);
            if (option2Button.getText().toString().equals(correctAnswer)) option2Button.setBackgroundColor(Color.GREEN);
            if (option3Button.getText().toString().equals(correctAnswer)) option3Button.setBackgroundColor(Color.GREEN);
            if (option4Button.getText().toString().equals(correctAnswer)) option4Button.setBackgroundColor(Color.GREEN);
        }

        // Passer à la question suivante après un délai
        new Handler().postDelayed(() -> {
            currentQuestionIndex++;
            displayQuestion();
        }, 2000); // 2 secondes de délai
    }

    private void showResults() {
        // Pour l'instant on affiche un simple Toast. Idéalement, on lancerait une `QuizResultActivity`
        String resultMessage = "Quiz terminé ! Votre score : " + score + "/" + questionList.size();
        Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show();
        // Fermer l'activité du quiz après un court délai
        new Handler().postDelayed(this::finish, 3000);
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            option1Button.setVisibility(View.GONE);
            option2Button.setVisibility(View.GONE);
            option3Button.setVisibility(View.GONE);
            option4Button.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.GONE);
            option1Button.setVisibility(View.VISIBLE);
            option2Button.setVisibility(View.VISIBLE);
            option3Button.setVisibility(View.VISIBLE);
            option4Button.setVisibility(View.VISIBLE);
        }
    }

    private void resetButtonColors() {
        option1Button.setBackgroundColor(Color.parseColor("#FF6200EE")); // Couleur par défaut du thème
        option2Button.setBackgroundColor(Color.parseColor("#FF6200EE"));
        option3Button.setBackgroundColor(Color.parseColor("#FF6200EE"));
        option4Button.setBackgroundColor(Color.parseColor("#FF6200EE"));
    }

    private void enableButtons(boolean enable) {
        option1Button.setEnabled(enable);
        option2Button.setEnabled(enable);
        option3Button.setEnabled(enable);
        option4Button.setEnabled(enable);
    }
}
