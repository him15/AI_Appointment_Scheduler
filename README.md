# 🩺 AI-Powered Appointment Scheduler Assistant

This project is a **Spring Boot backend service** that parses **natural language or document-based appointment requests** and converts them into structured scheduling data.

It supports:
- **Typed text input** (e.g., "Book dentist next Friday at 3pm")
- **Image input** (scanned notes, emails, photos) using OCR (Tesseract)

The pipeline: **OCR → Text Preprocessing → Entity Extraction → Normalization → Guardrails → JSON Output**

---

## 🚀 Tech Stack

- **Java 21**, **Spring Boot**
- **Maven** (build tool)
- **Tesseract OCR (Tess4J)** — extract text from images
- **Natty** — natural language date/time parser
- **Custom NLP** — fuzzy matching (Levenshtein) for department detection
- **REST API** — JSON endpoints
- **Ngrok / Cloudflare Tunnel** — expose local server for testing

---

## 🏗️ Architecture

```text
            ┌───────────────┐
            │   Client       │
            │ (Text / Image) │
            └───────┬───────┘
                    │
                    ▼
          ┌───────────────────┐
          │  REST Controller  │
          │ (ParseController) │
          └───────┬──────────┘
                  │
                  ▼
        ┌─────────────────────┐
        │   PipelineService   │
        └───────┬────────────┘
                │
 ┌──────────────┼───────────────────────────────────┐
 │              │                                   │
 ▼              ▼                                   ▼
TextPreprocessor   EntityExtractor (Fuzzy dept,   OCR Service
(lowercase,        date/time phrase)              (Tesseract + preprocessing)
normalize spaces)                                 
 │              │                                   │
 └───────┬──────┘                                   │
         ▼                                          │
    NattyNormalizer (ISO date/time)                 │
         │                                          │
         ▼                                          │
 SimpleConfidenceScorer (assign confidences)        │
         │                                          │
         ▼                                          │
 DefaultGuardrailService (decide status, message, final JSON)
         │
         ▼
  Structured JSON Response

<img width="849" height="694" alt="1" src="https://github.com/user-attachments/assets/460cf55b-188a-424c-8526-16fdfcf6663e" />


<img width="877" height="757" alt="2" src="https://github.com/user-attachments/assets/46f017fc-7660-45da-a976-a74919817514" />

