# Email SMTP Verification

Email verification extends the existing Redis verification service to support registration, code login, and password reset. SMTP settings are managed by super administrators through the system frontend; SMTP password is encrypted with the shared AES-GCM credential cipher and never returned. Local and test profiles keep non-network senders, while production uses JavaMail SMTP configured from the persisted settings. Existing SMS and password-login flows remain unchanged.

Sending failures roll back code, cooldown, failure, and daily-limit keys and return HTTP 503. Email targets use the existing email validation rules; unknown forgot-password targets reserve limits without delivery.
