// server.js - Express server que implementa:
// POST /calc/op  -> body { id?, op, a, b }  => { id, result, error }
// POST /calc/expr -> body { id?, expr }      => { id, result, error }

const express = require('express');
const cors = require('cors');

const app = express();
app.use(cors());                // opcional: facilita chamadas do navegador
app.use(express.json());        // parse application/json

const PORT = process.env.PORT || 8000;

// Helper de resposta
function jsonResponse(res, id, result, error, status = 200) {
    res.status(status).json({
        id: id !== undefined && id !== null ? id : 'unknown',
        result: result === undefined ? null : result,
        error: error === undefined ? null : error
    });
}

// /calc/op
app.post('/calc/op', (req, res) => {
    const body = req.body || {};
    const id = body.id || 'unknown';
    const op = body.op;
    const a = Number(body.a);
    const b = Number(body.b);

    if (!op || body.a === undefined || body.b === undefined) {
        return jsonResponse(res, id, null, 'Requisição inválida: esperado {op, a, b}', 400);
    }

    try {
        let r;
        switch (op) {
            case 'add': r = a + b; break;
            case 'sub': r = a - b; break;
            case 'mul': r = a * b; break;
            case 'div':
                if (b === 0) throw new Error('Divisão por zero');
                r = a / b; break;
            default:
                throw new Error('Operação inválida: ' + op);
        }
        jsonResponse(res, id, r, null, 200);
    } catch (err) {
        jsonResponse(res, id, null, err.message, 400);
    }
});

// /calc/expr -> avalia expressão completa (shunting-yard -> RPN -> eval)
app.post('/calc/expr', (req, res) => {
    const body = req.body || {};
    const id = body.id || 'unknown';
    const expr = body.expr;
    if (!expr) return jsonResponse(res, id, null, 'Requisição inválida: expected {expr}', 400);

    try {
        const result = evaluateExpression(String(expr));
        jsonResponse(res, id, result, null, 200);
    } catch (err) {
        jsonResponse(res, id, null, err.message, 400);
    }
});

app.listen(PORT, () => {
    console.log(`Calculadora JS server started at http://localhost:${PORT}`);
});

// ------------------ Helpers: tokenizer, shunting-yard and RPN eval ------------------

function evaluateExpression(expr) {
    const s = expr.replace(/\s+/g, '');
    if (s.length === 0) throw new Error('Expressão vazia');

    const tokens = [];
    let i = 0;
    const len = s.length;
    while (i < len) {
        const c = s[i];
        if (isDigit(c) || c === '.' || ((c === '+' || c === '-') && (i === 0 || s[i-1] === '(' || '+-*/'.includes(s[i-1])))) {
            let j = i + 1;
            while (j < len && (isDigit(s[j]) || s[j] === '.')) j++;
            tokens.push(s.substring(i, j));
            i = j;
        } else if ('+-*/()'.includes(c)) {
            tokens.push(c);
            i++;
        } else {
            throw new Error('Caracter inválido na expressão: ' + c);
        }
    }

    // shunting-yard -> RPN
    const output = [];
    const ops = [];
    for (const t of tokens) {
        if (!isNaN(Number(t))) {
            output.push(t);
        } else if ('+-*/'.includes(t)) {
            while (ops.length > 0) {
                const o2 = ops[ops.length - 1];
                if (o2 !== '(' && precedence(t) <= precedence(o2)) {
                    output.push(ops.pop());
                } else break;
            }
            ops.push(t);
        } else if (t === '(') {
            ops.push(t);
        } else if (t === ')') {
            while (ops.length > 0 && ops[ops.length - 1] !== '(') output.push(ops.pop());
            if (ops.length === 0 || ops[ops.length - 1] !== '(') throw new Error('Parênteses descompassados');
            ops.pop();
        }
    }
    while (ops.length > 0) {
        const o = ops.pop();
        if (o === '(' || o === ')') throw new Error('Parênteses descompassados');
        output.push(o);
    }

    // avaliar RPN
    const stack = [];
    for (const tok of output) {
        if (!isNaN(Number(tok))) {
            stack.push(Number(tok));
        } else {
            if (stack.length < 2) throw new Error('Expressão inválida');
            const b = stack.pop();
            const a = stack.pop();
            let r;
            switch (tok) {
                case '+': r = a + b; break;
                case '-': r = a - b; break;
                case '*': r = a * b; break;
                case '/':
                    if (b === 0) throw new Error('Divisão por zero');
                    r = a / b; break;
                default: throw new Error('Operador desconhecido: ' + tok);
            }
            stack.push(r);
        }
    }
    if (stack.length !== 1) throw new Error('Expressão inválida');
    return stack[0];
}

function isDigit(ch) { return /\d/.test(ch); }
function precedence(op) { return (op === '+' || op === '-') ? 1 : (op === '*' || op === '/') ? 2 : 0; }