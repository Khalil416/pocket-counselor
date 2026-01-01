// ============================================
// POCKET COUNSELOR - Frontend JavaScript
// ============================================

// Questions for the career assessment
// Each question has an ID, text, and multiple choice options
const questions = [
    {
        id: 'q1',
        text: 'What type of work environment do you prefer?',
        options: [
            { value: 'creative', label: 'Creative and artistic spaces' },
            { value: 'structured', label: 'Structured and organized' },
            { value: 'dynamic', label: 'Fast-paced and dynamic' },
            { value: 'collaborative', label: 'Team-oriented and collaborative' }
        ]
    },
    {
        id: 'q2',
        text: 'How do you prefer to solve problems?',
        options: [
            { value: 'analytical', label: 'Analyze data and find patterns' },
            { value: 'creative', label: 'Think outside the box' },
            { value: 'practical', label: 'Use hands-on approaches' },
            { value: 'collaborative', label: 'Work with others to brainstorm' }
        ]
    },
    {
        id: 'q3',
        text: 'What motivates you most in a career?',
        options: [
            { value: 'impact', label: 'Making a positive impact on others' },
            { value: 'growth', label: 'Continuous learning and growth' },
            { value: 'stability', label: 'Job security and stability' },
            { value: 'innovation', label: 'Creating something new' }
        ]
    },
    {
        id: 'q4',
        text: 'What are your strongest skills?',
        options: [
            { value: 'technical', label: 'Technical and analytical skills' },
            { value: 'communication', label: 'Communication and people skills' },
            { value: 'creative', label: 'Creative and design skills' },
            { value: 'leadership', label: 'Leadership and management skills' }
        ]
    },
    {
        id: 'q5',
        text: 'What is your ideal work-life balance?',
        options: [
            { value: 'flexible', label: 'Flexible hours and remote work' },
            { value: 'standard', label: 'Standard 9-to-5 schedule' },
            { value: 'intense', label: 'Intense periods with breaks' },
            { value: 'varied', label: 'Varied schedule with travel' }
        ]
    }
];

// Store user answers
let answers = {};
let currentQuestionIndex = 0;

// DOM Elements
const questionContainer = document.getElementById('questionContainer');
const progressFill = document.getElementById('progressFill');
const progressText = document.getElementById('progressText');
const resultContainer = document.getElementById('resultContainer');
const resultContent = document.getElementById('resultContent');
const loading = document.getElementById('loading');

// ============================================
// Initialize the questionnaire
// ============================================
function init() {
    showQuestion(0);
}

// ============================================
// Display a question
// ============================================
function showQuestion(index) {
    // Check if we've finished all questions
    if (index >= questions.length) {
        submitAnswers();
        return;
    }

    const question = questions[index];
    currentQuestionIndex = index;

    // Update progress bar
    const progress = ((index + 1) / questions.length) * 100;
    progressFill.style.width = progress + '%';
    progressText.textContent = `Question ${index + 1} of ${questions.length}`;

    // Create question HTML
    let html = `<div class="question-title">${question.text}</div>`;
    html += '<div class="answer-buttons">';

    // Create answer buttons
    question.options.forEach(option => {
        html += `
            <button class="answer-btn" onclick="selectAnswer('${question.id}', '${option.value}')">
                ${option.label}
            </button>
        `;
    });

    html += '</div>';

    // Update the container
    questionContainer.innerHTML = html;
}

// ============================================
// Handle answer selection
// ============================================
function selectAnswer(questionId, answerValue) {
    // Store the answer
    answers[questionId] = answerValue;

    // Move to next question after a short delay (for visual feedback)
    setTimeout(() => {
        showQuestion(currentQuestionIndex + 1);
    }, 300);
}

// ============================================
// Submit answers to backend
// ============================================
async function submitAnswers() {
    // Hide question container and show loading
    questionContainer.style.display = 'none';
    loading.style.display = 'block';

    try {
        // Prepare the data to send
        const requestData = {
            answers: answers
        };

        // Send POST request to backend
        // Note: Update this URL to match your backend server
        const response = await fetch('http://localhost:8080/api/analyze', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(requestData)
        });

        // Check if request was successful
        if (!response.ok) {
            throw new Error(`Server error: ${response.status}`);
        }

        // Parse the response
        const result = await response.json();

        // Display the results
        displayResults(result);

    } catch (error) {
        // Handle errors
        console.error('Error submitting answers:', error);
        displayError(error.message);
    } finally {
        // Hide loading spinner
        loading.style.display = 'none';
    }
}

// ============================================
// Display results from AI
// ============================================
function displayResults(result) {
    let html = '';

    // Display top 3 career paths
    if (result.careers && result.careers.length > 0) {
        result.careers.forEach(career => {
            html += `
                <div class="career-card">
                    <h3>${career.title}</h3>
                    <p>${career.description}</p>
                </div>
            `;
        });
    }

    // Display next steps
    if (result.nextSteps && result.nextSteps.length > 0) {
        html += `
            <div class="next-steps">
                <h3>Next Steps</h3>
                <ul>
        `;
        result.nextSteps.forEach(step => {
            html += `<li>${step}</li>`;
        });
        html += `
                </ul>
            </div>
        `;
    }

    // Update the result container
    resultContent.innerHTML = html;
    resultContainer.style.display = 'block';
}

// ============================================
// Display error message
// ============================================
function displayError(message) {
    resultContent.innerHTML = `
        <div class="career-card" style="border-left-color: #e74c3c;">
            <h3>Oops! Something went wrong</h3>
            <p>${message}</p>
            <p style="margin-top: 10px;">Please make sure the backend server is running.</p>
        </div>
    `;
    resultContainer.style.display = 'block';
}

// ============================================
// Start the application when page loads
// ============================================
window.addEventListener('DOMContentLoaded', init);

