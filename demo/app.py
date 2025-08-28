import streamlit as st
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import time
import io
from datetime import datetime, timedelta
import base64
import tempfile
from fpdf import FPDF


# Configure the page
st.set_page_config(
    page_title="FraudGuard Fusion Engine",
    page_icon="🛡️",
    layout="wide",
    initial_sidebar_state="expanded"
)

# Custom CSS for styling with light/dark theme support
st.markdown("""
<style>
    :root {
        --primary: #1f77b4;
        --secondary: #ff7f0e;
        --success: #2ecc71;
        --warning: #f39c12;
        --danger: #e74c3c;
        --light-bg: #f8f9fa;
        --dark-bg: #262730;
        --light-text: #f8f9fa;
        --dark-text: #212529;
        --card-bg: #ffffff;
        --card-shadow: rgba(0, 0, 0, 0.1);
    }
    
    @media (prefers-color-scheme: dark) {
        :root {
            --light-bg: #262730;
            --dark-text: #f8f9fa;
            --card-bg: #0e1117;
            --card-shadow: rgba(255, 255, 255, 0.1);
        }
    }
    
    .main-header {
        font-size: 2.5rem;
        color: var(--primary);
        text-align: center;
        margin-bottom: 1rem;
        transition: all 0.3s ease;
    }
    
    .score-card {
        border-radius: 0.5rem;
        padding: 1.5rem;
        box-shadow: 0 4px 6px var(--card-shadow);
        margin-bottom: 1rem;
        text-align: center;
        background-color: var(--card-bg);
        transition: all 0.3s ease;
    }
    
    .low-risk {
        border-left: 5px solid var(--success);
    }
    
    .medium-risk {
        border-left: 5px solid var(--warning);
    }
    
    .high-risk {
        border-left: 5px solid var(--danger);
    }
    
    .explanation-box {
        background-color: var(--card-bg);
        border-left: 4px solid var(--primary);
        padding: 1rem;
        margin: 1rem 0;
        border-radius: 0.5rem;
        box-shadow: 0 2px 4px var(--card-shadow);
        transition: all 0.3s ease;
    }
    
    .slider-container {
        padding: 1rem;
        background-color: var(--card-bg);
        border-radius: 0.5rem;
        margin: 1rem 0;
        box-shadow: 0 2px 4px var(--card-shadow);
        transition: all 0.3s ease;
    }
    
    .metric-card {
        background-color: var(--card-bg);
        padding: 1rem;
        border-radius: 0.5rem;
        box-shadow: 0 2px 4px var(--card-shadow);
        transition: all 0.3s ease;
    }
    
    .stButton button {
        transition: all 0.3s ease;
    }
    
    .stButton button:hover {
        transform: translateY(-2px);
        box-shadow: 0 4px 8px var(--card-shadow);
    }
    
    /* Animation for cards */
    @keyframes fadeIn {
        from { opacity: 0; transform: translateY(10px); }
        to { opacity: 1; transform: translateY(0); }
    }
    
    .score-card, .explanation-box, .slider-container, .metric-card {
        animation: fadeIn 0.5s ease-out;
    }
</style>
""", unsafe_allow_html=True)

# Initialize session state
def init_session_state():
    if 'touch_score' not in st.session_state:
        st.session_state.touch_score = 0.5
    if 'typing_score' not in st.session_state:
        st.session_state.typing_score = 0.5
    if 'usage_score' not in st.session_state:
        st.session_state.usage_score = 0.5
    if 'score_history' not in st.session_state:
        st.session_state.score_history = []
    if 'theme' not in st.session_state:
        st.session_state.theme = "light"

init_session_state()

# Utility functions
def calculate_final_score(touch_score, typing_score, usage_score):
    # Updated weights for 3-agent fusion: touch=0.5, typing=0.3, usage=0.2
    return 0.5 * touch_score + 0.3 * typing_score + 0.2 * usage_score

def determine_risk_level(score):
    if score <= 0.40:
        return "LOW", "low-risk"
    elif score <= 0.70:
        return "MEDIUM", "medium-risk"
    else:
        return "HIGH", "high-risk"

def get_touch_explanation(score):
    if score < 0.3:
        return "❌ Significant touch behavior anomalies detected. Unusual swipe patterns, tap pressure, or gesture velocity observed."
    elif score < 0.6:
        return "⚠️ Moderate touch behavior anomalies. Some deviations from typical user patterns."
    else:
        return "✅ Normal touch behavior patterns. Consistent with user's historical data."

def get_typing_explanation(score):
    if score < 0.3:
        return "❌ Significant typing behavior anomalies detected. Irregular keystroke timing, unusual dwell times, or robotic patterns observed."
    elif score < 0.6:
        return "⚠️ Moderate typing behavior anomalies. Some deviations from typical keystroke dynamics."
    else:
        return "✅ Normal typing behavior patterns. Consistent keystroke rhythm and timing."

def get_usage_explanation(score):
    if score < 0.3:
        return "❌ Significant usage pattern anomalies detected. Unusual app access times, sequences, or durations."
    elif score < 0.6:
        return "⚠️ Moderate usage pattern anomalies. Some deviations from typical usage patterns."
    else:
        return "✅ Normal usage patterns. Consistent with user's historical behavior."

def generate_random_data():
    st.session_state.touch_score = round(np.random.uniform(0, 1), 2)
    st.session_state.typing_score = round(np.random.uniform(0, 1), 2)
    st.session_state.usage_score = round(np.random.uniform(0, 1), 2)
    # Add to history
    final_score = calculate_final_score(
        st.session_state.touch_score,
        st.session_state.typing_score, 
        st.session_state.usage_score
    )
    risk_level, _ = determine_risk_level(final_score)
    timestamp = datetime.now()
    st.session_state.score_history.append({
        'timestamp': timestamp,
        'touch_score': st.session_state.touch_score,
        'typing_score': st.session_state.typing_score,
        'usage_score': st.session_state.usage_score,
        'final_score': final_score,
        'risk_level': risk_level
    })

def create_radar_chart(touch_score, typing_score, usage_score, final_score):
    categories = ['Touch Score', 'Typing Score', 'Usage Score', 'Fusion Score']
    values = [touch_score, typing_score, usage_score, final_score]
    
    # Complete the circle for radar chart
    values += values[:1]
    categories += categories[:1]
    
    # Create figure
    fig, ax = plt.subplots(figsize=(8, 8), subplot_kw=dict(polar=True))
    
    # Draw the chart
    angles = np.linspace(0, 2*np.pi, len(categories)).tolist()
    ax.plot(angles, values, color='#1f77b4', linewidth=2)
    ax.fill(angles, values, color='#1f77b4', alpha=0.25)
    
    # Set labels
    ax.set_yticklabels([])
    ax.set_xticks(angles[:-1])
    ax.set_xticklabels(categories[:-1])
    
    # Set title
    plt.title('Fraud Risk Assessment', size=14, color='#1f77b4', y=1.1)
    
    return fig

def create_gauge_chart(final_score):
    fig, ax = plt.subplots(figsize=(10, 4))
    
    # Create gradient background for gauge
    gradient = np.linspace(0, 1, 100).reshape(1, -1)
    gradient = np.vstack((gradient, gradient))
    
    ax.imshow(gradient, aspect='auto', cmap='RdYlGn_r', extent=(0, 1, 0, 1))
    ax.set_title('Overall Risk Assessment Gauge')
    ax.axvline(x=final_score, color='black', linestyle='--', linewidth=2)
    ax.text(final_score, 1.1, f'{final_score:.2f}', ha='center', fontweight='bold')
    
    # Add risk level markers
    ax.axvline(x=0.4, color='gray', linestyle='-', alpha=0.7)
    ax.axvline(x=0.7, color='gray', linestyle='-', alpha=0.7)
    ax.text(0.2, -0.2, 'LOW', ha='center', fontweight='bold')
    ax.text(0.55, -0.2, 'MEDIUM', ha='center', fontweight='bold')
    ax.text(0.85, -0.2, 'HIGH', ha='center', fontweight='bold')
    
    ax.set_yticks([])
    ax.set_xlim(0, 1)
    ax.set_xlabel('Risk Score')
    
    return fig

def create_trend_chart(history):
    if not history:
        return None
        
    df = pd.DataFrame(history)
    df.set_index('timestamp', inplace=True)
    
    fig, ax = plt.subplots(figsize=(10, 6))
    ax.plot(df.index, df['touch_score'], label='Touch Score', marker='o')
    ax.plot(df.index, df['typing_score'], label='Typing Score', marker='d')
    ax.plot(df.index, df['usage_score'], label='Usage Score', marker='s')
    ax.plot(df.index, df['final_score'], label='Fusion Score', marker='^', linewidth=2)
    
    ax.axhline(y=0.4, color='green', linestyle='--', alpha=0.7, label='Low Risk Threshold')
    ax.axhline(y=0.7, color='orange', linestyle='--', alpha=0.7, label='Medium Risk Threshold')
    
    ax.set_ylim(0, 1)
    ax.set_ylabel('Score')
    ax.set_xlabel('Time')
    ax.set_title('Score Trends Over Time')
    ax.legend()
    ax.grid(True, alpha=0.3)
    
    plt.xticks(rotation=45)
    plt.tight_layout()
    
    return fig

def generate_csv():
    if st.session_state.score_history:
        df = pd.DataFrame(st.session_state.score_history)
        return df.to_csv(index=False)
    return None

def generate_pdf_report(touch_score, typing_score, usage_score, final_score, risk_level):
    pdf = FPDF()
    pdf.add_page()
    
    # Add Unicode font from your fonts folder
    pdf.add_font('DejaVu', '', 'fonts/DejaVuSans.ttf', uni=True)
    
    # Title
    pdf.set_font('DejaVu', '', 16)
    pdf.cell(0, 10, "FraudGuard Fusion Engine Report", 0, 1, 'C')
    pdf.ln(10)
    
    # Date
    pdf.set_font('DejaVu', '', 12)
    pdf.cell(0, 10, f"Report generated on: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}", 0, 1)
    pdf.ln(5)
    
    # Scores
    pdf.set_font('DejaVu', '', 14)
    pdf.cell(0, 10, "Risk Assessment Scores", 0, 1)
    pdf.set_font('DejaVu', '', 12)
    pdf.cell(0, 10, f"Touch Score: {touch_score:.2f}", 0, 1)
    pdf.cell(0, 10, f"Typing Score: {typing_score:.2f}", 0, 1)
    pdf.cell(0, 10, f"Usage Score: {usage_score:.2f}", 0, 1)
    pdf.cell(0, 10, f"Final Score: {final_score:.2f}", 0, 1)
    pdf.cell(0, 10, f"Risk Level: {risk_level}", 0, 1)
    pdf.ln(10)
    
    # Explanations
    pdf.set_font('DejaVu', '', 14)
    pdf.cell(0, 10, "Anomaly Analysis", 0, 1)
    pdf.set_font('DejaVu', '', 12)
    pdf.multi_cell(0, 10, f"Touch Analysis: {get_touch_explanation(touch_score)}")
    pdf.multi_cell(0, 10, f"Typing Analysis: {get_typing_explanation(typing_score)}")
    pdf.multi_cell(0, 10, f"Usage Analysis: {get_usage_explanation(usage_score)}")
    
    # Save to temporary file
    temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.pdf')
    pdf.output(temp_file.name)
    return temp_file.name

def process_uploaded_file(uploaded_file):
    try:
        df = pd.read_csv(uploaded_file)
        # Simple validation
        required_cols = ['timestamp', 'touch_score', 'typing_score', 'usage_score']
        if not all(col in df.columns for col in required_cols):
            st.error("CSV must contain 'timestamp', 'touch_score', 'typing_score', and 'usage_score' columns")
            return None
            
        # Calculate final scores and risk levels
        df['final_score'] = 0.5 * df['touch_score'] + 0.3 * df['typing_score'] + 0.2 * df['usage_score']
        df['risk_level'] = df['final_score'].apply(
            lambda x: 'LOW' if x <= 0.4 else 'MEDIUM' if x <= 0.7 else 'HIGH'
        )
        
        return df
    except Exception as e:
        st.error(f"Error processing file: {str(e)}")
        return None

# Header
st.markdown('<h1 class="main-header">🛡️ FraudGuard Fusion Engine</h1>', unsafe_allow_html=True)
st.markdown("### Advanced Behavioral Biometrics for Fraud Detection")

# Sidebar for additional controls
with st.sidebar:
    st.header("Settings & Controls")
    
    # Theme toggle
    st.session_state.theme = st.selectbox(
        "Theme", 
        ["light", "dark"],
        help="Choose light or dark theme"
    )
    
    # Random data button
    if st.button("🎲 Simulate Random User Data", use_container_width=True):
        generate_random_data()
        st.rerun()
    
    # File uploader
    uploaded_file = st.file_uploader(
        "Upload CSV for Analysis", 
        type=['csv'],
        help="CSV should contain timestamp, touch_score, typing_score, and usage_score columns"
    )
    
    if uploaded_file is not None:
        processed_data = process_uploaded_file(uploaded_file)
        if processed_data is not None:
            st.success("File uploaded successfully!")
            st.dataframe(processed_data.head(), use_container_width=True)
            
            # Add to history
            for _, row in processed_data.iterrows():
                st.session_state.score_history.append({
                    'timestamp': row['timestamp'],
                    'touch_score': row['touch_score'],
                    'typing_score': row['typing_score'],
                    'usage_score': row['usage_score'],
                    'final_score': row['final_score'],
                    'risk_level': row['risk_level']
                })
    
    # Export options
    st.header("Export Results")
    col1, col2 = st.columns(2)
    
    with col1:
        if st.download_button(
            label="📄 Export CSV",
            data=generate_csv() or "",
            file_name=f"fraudguard_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.csv",
            mime="text/csv",
            disabled=not st.session_state.score_history,
            use_container_width=True
        ):
            st.success("CSV exported successfully!")
    
    with col2:
        if st.button("📊 Generate PDF Report", use_container_width=True):
            final_score = calculate_final_score(
                st.session_state.touch_score,
                st.session_state.typing_score, 
                st.session_state.usage_score
            )
            risk_level, _ = determine_risk_level(final_score)
            
            pdf_path = generate_pdf_report(
                st.session_state.touch_score,
                st.session_state.typing_score,
                st.session_state.usage_score,
                final_score,
                risk_level
            )
            
            with open(pdf_path, "rb") as f:
                pdf_data = f.read()
            
            st.download_button(
                label="⬇️ Download PDF",
                data=pdf_data,
                file_name=f"fraudguard_report_{datetime.now().strftime('%Y%m%d_%H%M%S')}.pdf",
                mime="application/pdf",
                use_container_width=True
            )

# Create two columns for the layout
col1, col2 = st.columns([1, 1])

with col1:
    st.markdown("### Configuration Panel")
    
    # Sliders for scores
    st.markdown('<div class="slider-container">', unsafe_allow_html=True)
    
    # Touch score slider with unique key
    touch_score = st.slider(
        "Touch Behavior Score", 
        min_value=0.0, 
        max_value=1.0, 
        value=st.session_state.touch_score,
        step=0.01,
        key="main_touch_score_slider",
        help="Measure of how typical the user's touch patterns are (0 = anomalous, 1 = normal)"
    )
    st.session_state.touch_score = touch_score
    
    # Typing score slider with unique key
    typing_score = st.slider(
        "Typing Behavior Score", 
        min_value=0.0, 
        max_value=1.0, 
        value=st.session_state.typing_score,
        step=0.01,
        key="main_typing_score_slider",
        help="Measure of how typical the user's keystroke dynamics are (0 = anomalous, 1 = normal)"
    )
    st.session_state.typing_score = typing_score
    
    # Usage score slider with unique key
    usage_score = st.slider(
        "Usage Pattern Score", 
        min_value=0.0, 
        max_value=1.0, 
        value=st.session_state.usage_score,
        step=0.01,
        key="main_usage_score_slider",
        help="Measure of how typical the user's app usage patterns are (0 = anomalous, 1 = normal)"
    )
    st.session_state.usage_score = usage_score
    st.markdown('</div>', unsafe_allow_html=True)
    
    # Calculate final score
    final_score = calculate_final_score(
        st.session_state.touch_score,
        st.session_state.typing_score, 
        st.session_state.usage_score
    )
    
    # Determine risk level
    risk_level, risk_class = determine_risk_level(final_score)
    
    # Display metrics
    col1_1, col1_2, col1_3, col1_4 = st.columns(4)
    
    with col1_1:
        st.metric(
            label="Touch Score", 
            value=f"{st.session_state.touch_score:.2f}",
            delta=None,
            help="User's touch behavior anomaly score"
        )
    
    with col1_2:
        st.metric(
            label="Typing Score", 
            value=f"{st.session_state.typing_score:.2f}",
            delta=None,
            help="User's keystroke dynamics anomaly score"
        )
    
    with col1_3:
        st.metric(
            label="Usage Score", 
            value=f"{st.session_state.usage_score:.2f}",
            delta=None,
            help="User's usage pattern anomaly score"
        )
    
    with col1_4:
        st.metric(
            label="Fusion Score", 
            value=f"{final_score:.2f}",
            delta=None,
            help="Combined risk score"
        )
    
    # Progress bar for risk level
    st.markdown(f"**Risk Level: {risk_level}**")
    if risk_level == "LOW":
        st.progress(final_score / 0.4, text="Low Risk")
    elif risk_level == "MEDIUM":
        st.progress((final_score - 0.4) / 0.3, text="Medium Risk")
    else:
        st.progress((final_score - 0.7) / 0.3, text="High Risk")
    
    # Display final score and risk level in a card
    st.markdown(f'<div class="score-card {risk_class}">', unsafe_allow_html=True)
    st.markdown("### Fusion Engine Result")
    st.markdown(f"#### Final Score: {final_score:.2f}")
    st.markdown(f"#### Risk Level: **{risk_level}**")
    st.markdown('</div>', unsafe_allow_html=True)
    
    # Generate explanations based on scores
    st.markdown("### Anomaly Analysis")
    
    touch_explanation = get_touch_explanation(st.session_state.touch_score)
    typing_explanation = get_typing_explanation(st.session_state.typing_score)
    usage_explanation = get_usage_explanation(st.session_state.usage_score)
    
    st.markdown('<div class="explanation-box">', unsafe_allow_html=True)
    st.markdown(f"**Touch Analysis:** {touch_explanation}")
    st.markdown('</div>', unsafe_allow_html=True)
    
    st.markdown('<div class="explanation-box">', unsafe_allow_html=True)
    st.markdown(f"**Typing Analysis:** {typing_explanation}")
    st.markdown('</div>', unsafe_allow_html=True)
    
    st.markdown('<div class="explanation-box">', unsafe_allow_html=True)
    st.markdown(f"**Usage Analysis:** {usage_explanation}")
    st.markdown('</div>', unsafe_allow_html=True)
    
    # Display recommendation based on risk level
    st.markdown("### Recommended Action")
    if risk_level == "LOW":
        st.success("✅ No action required. User behavior appears normal. Continue monitoring with standard protocols.")
    elif risk_level == "MEDIUM":
        st.warning("⚠️ Enhanced verification recommended. Consider requesting additional authentication factors.")
    else:
        st.error("🚨 Immediate action required. High probability of fraudulent activity. Initiate account protection protocols.")

with col2:
    st.markdown("### Risk Visualization")
    
    # Create and display radar chart
    radar_fig = create_radar_chart(
        st.session_state.touch_score,
        st.session_state.typing_score, 
        st.session_state.usage_score, 
        final_score
    )
    st.pyplot(radar_fig)
    
    # Create and display gauge chart
    gauge_fig = create_gauge_chart(final_score)
    st.pyplot(gauge_fig)
    
    # Score history trend chart
    if st.session_state.score_history:
        st.markdown("### Score Trends")
        trend_fig = create_trend_chart(st.session_state.score_history)
        if trend_fig:
            st.pyplot(trend_fig)
    
    # History table
    if st.session_state.score_history:
        st.markdown("### Assessment History")
        history_df = pd.DataFrame(st.session_state.score_history)
        # Format timestamp for display
        history_display = history_df.copy()
        history_display['timestamp'] = history_display['timestamp'].apply(
            lambda x: x.strftime('%Y-%m-%d %H:%M:%S') if isinstance(x, datetime) else x
        )
        st.dataframe(
            history_display.tail(5), 
            use_container_width=True,
            hide_index=True
        )

# Footer
st.markdown("---")
st.markdown("🛡️ **FraudGuard Fusion Engine** - Behavioral Biometrics Analysis System | v2.0")