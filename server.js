const express = require('express');
const axios = require('axios');
const app = express();
const port = 3000;

require('dotenv').config();

// Middleware to parse JSON bodies
app.use(express.json());

// Constants
const AUTH_URL = 'https://auth.dev.izzie.io/v0/oauth2/token';
const SESSION_URL = 'https://api.dev.izzie.io/v0/sessions';

app.post('/createPaymentIntent', async (req, res) => {
    try {
        // Validate input
        const { price } = req.body;
        if (!price) {
            return res.status(400).json({ error: 'Price is required' });
        }

        // Step 1: Create Token
        const tokenResponse = await axios.post(`${AUTH_URL}?grant_type=client_credentials`, {}, {
            headers: {
                'Authorization': 'Basic ' + process.env.CREDENTIAL,
                'Content-Type': 'application/x-www-form-urlencoded'
            }
        });

        const accessToken = tokenResponse.data.access_token;

        // Step 2: Create Session
        const sessionResponse = await axios.post(`${SESSION_URL}?scope=izzie-tokenize`, {
            "type": "bemobi_transparent",
            "channel": "WEB",
            "customer": {
                "name": "teste",
                "unit": "3017752",
                "email": "test-8102001@bemobi.com",
                "document_type": "cpf",
                "phone_numbers": [
                    {
                        "ddd": "21",
                        "ddi": "55",
                        "number": "987654321"
                    }
                ],
                "document_number": "012345678-90"
            },
            "duration": 10080,
            "invoices": [
                {
                    "id": "8102001",
                    "amount": {
                        "value": price, // Using the dynamic price here
                        "currency_code": "BRL"
                    },
                    "due_date": "2025-07-13",
                    "reference_month": "2025/07",
                    "payment_description": "2024 - E.F 5º ANO"
                }
            ],
            "corporate": {
                "id": "74.000.738/0018-33",
                "name": "Bemobi",
                "division": "38-4"
            },
            "min_installment_value": 1,
            "max_number_of_installments": 24
        }, {
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${accessToken}`
            }
        });

        res.json(sessionResponse.data);

    } catch (error) {
        console.error('Error:', error.response?.data || error.message);
        res.status(500).json({
            error: 'Failed to create payment intent',
            details: error.response?.data || error.message
        });
    }
});

app.listen(port, () => {
    console.log(`Server running at http://localhost:${port}`);
});