/**
 * RESULTS UI LOGIC
 * Renders exactly what AI returns — code does not touch or reformat.
 * Displays: overall_summary, categories as bars, strongest_areas, growth_areas.
 */

/**
 * Render the AI-generated results (from PROMPT B)
 * @param {Object} data - The raw JSON returned by AI
 */
function renderResults(data) {
    try {
        if (!data || typeof data !== 'object') {
            console.error('renderResults: invalid data', data);
            return;
        }

        const profileBadge = document.getElementById('profileBadge');
        const resultsWarning = document.getElementById('resultsWarning');
        const summaryEl = document.getElementById('overallSummary');
        const barsContainer = document.getElementById('skillBars');
        const strengthsContainer = document.getElementById('strengthsList');
        const attentionContainer = document.getElementById('attentionList');
        const strengthsSection = document.getElementById('strengthsSection');
        const attentionSection = document.getElementById('attentionSection');

        if (!profileBadge || !summaryEl) {
            console.warn('Results DOM elements missing');
            return;
        }

        // Profile quality badge
        profileBadge.textContent = data.profile_quality || 'Profile';

        // Data quality note / warning
        if (resultsWarning) {
            if (data.data_quality_note) {
                resultsWarning.textContent = data.data_quality_note;
                resultsWarning.style.display = 'block';
            } else {
                resultsWarning.style.display = 'none';
            }
        }

        // Overall summary
        summaryEl.textContent = data.overall_summary || '';

        // ─── Categories as skill bars ─────────────────────
        if (barsContainer) barsContainer.innerHTML = '';

        if (data.categories && data.categories.length > 0) {
            data.categories.forEach((cat, index) => {
                const item = document.createElement('div');
                item.className = 'skill-bar-item';
                item.style.animationDelay = `${index * 0.1}s`;

                const labelClass = cat.label.toLowerCase();

                item.innerHTML = `
        <div class="skill-bar-header">
          <span class="skill-bar-name">${cat.name}</span>
          <div class="skill-bar-stats">
            <span class="skill-bar-percentage">${cat.score}%</span>
            <span class="skill-bar-label ${labelClass}">${cat.label}</span>
          </div>
        </div>
        <div class="skill-bar-description">${cat.explanation}</div>
        <div class="skill-bar-track">
          <div class="skill-bar-fill ${labelClass}" style="width: 0%"></div>
        </div>
      `;

                if (barsContainer) barsContainer.appendChild(item);

                // Animate bar fill
                setTimeout(() => {
                    item.querySelector('.skill-bar-fill').style.width = `${cat.score}%`;
                }, 200 + (index * 120));
            });
        }

        // ─── Strongest areas ─────────────────────────────
        if (data.strongest_areas && data.strongest_areas.length > 0 && strengthsSection && strengthsContainer) {
            strengthsSection.style.display = 'block';
            strengthsContainer.innerHTML = '';
            data.strongest_areas.forEach(area => {
                const li = document.createElement('li');
                li.textContent = area.reason;
                strengthsContainer.appendChild(li);
            });
        } else if (strengthsSection) {
            strengthsSection.style.display = 'none';
        }

        // ─── Needs attention ─────────────────────────────
        if (data.growth_areas && data.growth_areas.length > 0 && attentionSection && attentionContainer) {
            attentionSection.style.display = 'block';
            attentionContainer.innerHTML = '';
            data.growth_areas.forEach(area => {
                const li = document.createElement('li');
                li.textContent = area.reason;
                attentionContainer.appendChild(li);
            });
        } else if (attentionSection) {
            attentionSection.style.display = 'none';
        }

        console.log('Results rendered successfully');
    } catch (e) {
        console.error('Error rendering results:', e);
        document.getElementById('overallSummary').innerHTML = `<div class="error-box">
        <p>There was a problem displaying your results.</p>
        <small>${e.message}</small>
    </div>`;
    }
}
