/**
 * JARVIS AI — Frontend Application Logic
 * Handles command processing, voice interaction, and real-time updates.
 */
(function () {
    'use strict';

    // ========================================
    // State
    // ========================================
    const state = {
        commandCount: 0,
        totalLatency: 0,
        startTime: Date.now(),
        isRecording: false,
        supportedActions: []
    };

    // ========================================
    // DOM Elements
    // ========================================
    const $ = (sel) => document.querySelector(sel);
    const $$ = (sel) => document.querySelectorAll(sel);

    const els = {
        commandInput: $('#commandInput'),
        btnSend: $('#btnSend'),
        btnVoice: $('#btnVoice'),
        btnClearHistory: $('#btnClearHistory'),
        conversationArea: $('#conversationArea'),
        toggleSpeak: $('#toggleSpeak'),
        voiceOverlay: $('#voiceOverlay'),
        voiceStatus: $('#voiceStatus'),
        btnVoiceStop: $('#btnVoiceStop'),
        systemTime: $('#systemTime'),
        statusIndicator: $('#statusIndicator'),
        actionCountVal: $('#actionCountVal'),
        commandCountVal: $('#commandCountVal'),
        latencyVal: $('#latencyVal'),
        uptimeVal: $('#uptimeVal'),
        actionsGrid: $('#actionsGrid'),
        activityList: $('#activityList')
    };

    // ========================================
    // Initialization
    // ========================================
    function init() {
        setupEventListeners();
        updateClock();
        setInterval(updateClock, 1000);
        setInterval(updateUptime, 1000);
        fetchStatus();
        initParticles();
        updateGreeting();
    }

    // ========================================
    // Event Listeners
    // ========================================
    function setupEventListeners() {
        // Send command on button click
        els.btnSend.addEventListener('click', sendCommand);

        // Send command on Enter
        els.commandInput.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                sendCommand();
            }
        });

        // Voice button
        els.btnVoice.addEventListener('click', startVoiceCapture);
        els.btnVoiceStop.addEventListener('click', stopVoiceCapture);

        // Clear history
        els.btnClearHistory.addEventListener('click', clearHistory);

        // Quick commands
        $$('.quick-cmd').forEach(btn => {
            btn.addEventListener('click', () => {
                els.commandInput.value = btn.dataset.cmd;
                sendCommand();
            });
        });
    }

    // ========================================
    // Command Processing
    // ========================================
    async function sendCommand() {
        const text = els.commandInput.value.trim();
        if (!text) return;

        // Clear input
        els.commandInput.value = '';

        // Add user message to conversation
        addMessage('user', text);

        // Show loading
        const loadingId = addLoadingMessage();

        try {
            const response = await fetch('/api/jarvis/command', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    text: text,
                    speakResponse: els.toggleSpeak.checked
                })
            });

            const data = await response.json();

            // Remove loading
            removeLoadingMessage(loadingId);

            // Add Jarvis response
            addMessage('jarvis', data.responseText, {
                success: data.success,
                time: data.totalTimeMs,
                action: data.intent?.action
            });

            // Update stats
            state.commandCount++;
            state.totalLatency += data.totalTimeMs || 0;
            updateStats(data);
            addActivity(data);

        } catch (error) {
            removeLoadingMessage(loadingId);
            addMessage('jarvis', 'Connection error. Is the Jarvis server running?', {
                success: false
            });
        }
    }

    // ========================================
    // Voice Capture
    // ========================================
    async function startVoiceCapture() {
        state.isRecording = true;
        els.voiceOverlay.classList.add('active');
        els.btnVoice.classList.add('recording');
        els.voiceStatus.textContent = 'Listening...';

        try {
            const response = await fetch('/api/jarvis/voice', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ durationMs: 5000 })
            });

            const data = await response.json();

            closeVoiceOverlay();

            if (data.transcribedText) {
                addMessage('user', data.transcribedText);
            }

            addMessage('jarvis', data.responseText, {
                success: data.success,
                time: data.totalTimeMs,
                action: data.intent?.action
            });

            state.commandCount++;
            state.totalLatency += data.totalTimeMs || 0;
            updateStats(data);
            addActivity(data);

        } catch (error) {
            closeVoiceOverlay();
            addMessage('jarvis', 'Voice capture failed. Check microphone permissions.', {
                success: false
            });
        }
    }

    function stopVoiceCapture() {
        els.voiceStatus.textContent = 'Processing...';
        // The POST request will naturally complete
    }

    function closeVoiceOverlay() {
        state.isRecording = false;
        els.voiceOverlay.classList.remove('active');
        els.btnVoice.classList.remove('recording');
    }

    // ========================================
    // Chat Messages
    // ========================================
    function addMessage(type, text, meta = {}) {
        // Remove welcome message if present
        const welcome = els.conversationArea.querySelector('.welcome-message');
        if (welcome) welcome.remove();

        const time = new Date().toLocaleTimeString('en-US', {
            hour: '2-digit', minute: '2-digit'
        });

        const avatar = type === 'jarvis' ? 'J' : 'U';

        let metaHTML = `<span class="message-time">${time}</span>`;
        if (meta.time) {
            metaHTML += `<span>• ${meta.time}ms</span>`;
        }
        if (meta.action) {
            metaHTML += `<span>• ${meta.action}</span>`;
        }
        if (meta.success !== undefined) {
            const statusClass = meta.success ? 'success' : 'error';
            const statusIcon = meta.success ? '✓' : '✗';
            metaHTML += `<span class="message-status ${statusClass}">${statusIcon}</span>`;
        }

        const messageEl = document.createElement('div');
        messageEl.className = `message ${type}`;
        messageEl.innerHTML = `
            <div class="message-avatar">${avatar}</div>
            <div class="message-content">
                <div class="message-text">${escapeHTML(text)}</div>
                <div class="message-meta">${metaHTML}</div>
            </div>
        `;

        els.conversationArea.appendChild(messageEl);
        els.conversationArea.scrollTop = els.conversationArea.scrollHeight;
    }

    function addLoadingMessage() {
        const id = 'loading-' + Date.now();
        const el = document.createElement('div');
        el.className = 'message jarvis';
        el.id = id;
        el.innerHTML = `
            <div class="message-avatar">J</div>
            <div class="message-content">
                <div class="message-loading">
                    <span></span><span></span><span></span>
                </div>
            </div>
        `;
        els.conversationArea.appendChild(el);
        els.conversationArea.scrollTop = els.conversationArea.scrollHeight;
        return id;
    }

    function removeLoadingMessage(id) {
        const el = document.getElementById(id);
        if (el) el.remove();
    }

    function clearHistory() {
        els.conversationArea.innerHTML = '';
        state.commandCount = 0;
        state.totalLatency = 0;
        els.commandCountVal.textContent = '0';
        els.latencyVal.textContent = '—';
        els.activityList.innerHTML = '<div class="activity-empty">No activity yet. Try a command!</div>';
    }

    // ========================================
    // Dashboard Updates
    // ========================================
    async function fetchStatus() {
        try {
            const response = await fetch('/api/jarvis/status');
            const data = await response.json();

            state.supportedActions = data.supportedActions || [];
            els.actionCountVal.textContent = data.actionCount || 0;

            // Render action tags
            els.actionsGrid.innerHTML = state.supportedActions
                .sort()
                .map(a => `<span class="action-tag">${a}</span>`)
                .join('');

            // Update status indicator
            els.statusIndicator.querySelector('.status-text').textContent =
                data.status === 'online' ? 'ONLINE' : 'OFFLINE';

        } catch (error) {
            els.statusIndicator.querySelector('.status-text').textContent = 'OFFLINE';
            els.statusIndicator.querySelector('.status-dot').style.background = 'var(--danger)';
        }
    }

    function updateStats(data) {
        els.commandCountVal.textContent = state.commandCount;
        const avgLatency = Math.round(state.totalLatency / state.commandCount);
        els.latencyVal.textContent = avgLatency + 'ms';
    }

    function addActivity(data) {
        const empty = els.activityList.querySelector('.activity-empty');
        if (empty) empty.remove();

        const time = new Date().toLocaleTimeString('en-US', {
            hour: '2-digit', minute: '2-digit', second: '2-digit'
        });

        const item = document.createElement('div');
        item.className = 'activity-item';
        item.innerHTML = `
            <span class="activity-dot ${data.success ? 'success' : 'error'}"></span>
            <span class="activity-text">${escapeHTML(data.intent?.action || 'UNKNOWN')}: ${escapeHTML(truncate(data.transcribedText || '', 35))}</span>
            <span class="activity-time">${time}</span>
        `;

        // Insert at top
        els.activityList.insertBefore(item, els.activityList.firstChild);

        // Keep max 10 items
        while (els.activityList.children.length > 10) {
            els.activityList.removeChild(els.activityList.lastChild);
        }
    }

    // ========================================
    // Clock & Uptime
    // ========================================
    function updateClock() {
        const now = new Date();
        els.systemTime.textContent = now.toLocaleTimeString('en-US', {
            hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true
        });
    }

    function updateUptime() {
        const elapsed = Math.floor((Date.now() - state.startTime) / 1000);
        const h = Math.floor(elapsed / 3600);
        const m = Math.floor((elapsed % 3600) / 60);
        const s = elapsed % 60;

        if (h > 0) {
            els.uptimeVal.textContent = `${h}h ${m}m`;
        } else if (m > 0) {
            els.uptimeVal.textContent = `${m}m ${s}s`;
        } else {
            els.uptimeVal.textContent = `${s}s`;
        }
    }

    function updateGreeting() {
        const hour = new Date().getHours();
        let greeting;
        if (hour < 12) greeting = 'Good morning, Sir.';
        else if (hour < 17) greeting = 'Good afternoon, Sir.';
        else greeting = 'Good evening, Sir.';

        const h3 = els.conversationArea.querySelector('.welcome-message h3');
        if (h3) h3.textContent = greeting;
    }

    // ========================================
    // Particle Background
    // ========================================
    function initParticles() {
        const canvas = document.getElementById('particles');
        const ctx = canvas.getContext('2d');
        const particles = [];
        const PARTICLE_COUNT = 60;

        function resize() {
            canvas.width = window.innerWidth;
            canvas.height = window.innerHeight;
        }

        resize();
        window.addEventListener('resize', resize);

        class Particle {
            constructor() {
                this.reset();
            }

            reset() {
                this.x = Math.random() * canvas.width;
                this.y = Math.random() * canvas.height;
                this.vx = (Math.random() - 0.5) * 0.3;
                this.vy = (Math.random() - 0.5) * 0.3;
                this.radius = Math.random() * 1.5 + 0.5;
                this.opacity = Math.random() * 0.4 + 0.1;
            }

            update() {
                this.x += this.vx;
                this.y += this.vy;

                if (this.x < 0 || this.x > canvas.width) this.vx *= -1;
                if (this.y < 0 || this.y > canvas.height) this.vy *= -1;
            }

            draw() {
                ctx.beginPath();
                ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
                ctx.fillStyle = `hsla(199, 100%, 60%, ${this.opacity})`;
                ctx.fill();
            }
        }

        for (let i = 0; i < PARTICLE_COUNT; i++) {
            particles.push(new Particle());
        }

        function drawConnections() {
            for (let i = 0; i < particles.length; i++) {
                for (let j = i + 1; j < particles.length; j++) {
                    const dx = particles[i].x - particles[j].x;
                    const dy = particles[i].y - particles[j].y;
                    const dist = Math.sqrt(dx * dx + dy * dy);

                    if (dist < 150) {
                        const opacity = (1 - dist / 150) * 0.12;
                        ctx.beginPath();
                        ctx.moveTo(particles[i].x, particles[i].y);
                        ctx.lineTo(particles[j].x, particles[j].y);
                        ctx.strokeStyle = `hsla(199, 100%, 60%, ${opacity})`;
                        ctx.lineWidth = 0.5;
                        ctx.stroke();
                    }
                }
            }
        }

        function animate() {
            ctx.clearRect(0, 0, canvas.width, canvas.height);

            particles.forEach(p => {
                p.update();
                p.draw();
            });

            drawConnections();
            requestAnimationFrame(animate);
        }

        animate();
    }

    // ========================================
    // Utilities
    // ========================================
    function escapeHTML(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    function truncate(str, len) {
        return str.length > len ? str.substring(0, len) + '...' : str;
    }

    // ========================================
    // Boot
    // ========================================
    document.addEventListener('DOMContentLoaded', init);

})();
