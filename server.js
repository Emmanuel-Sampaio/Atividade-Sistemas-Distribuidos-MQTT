// server.js
// Servidor Express para a calculadora HTTP
// Endpoints:
//   POST /calc/op   { id, op, a, b }   -> op in [add, sub, mul, div]
//   POST /calc/expr { id, expr }       -> avalia expressão aritmética
//
// Uso:
//   npm init -y
//   npm install express
//   node server.js
//
// Escuta na porta 8000 por padrão

const express = require('express');
const app = express();
const PORT = process.env.PORT || 8000;

// Middlewares
app.use(express.json()); // body parser

// Middleware para capturar JSON malformado (body-parser / express.json)
app.use((err, req, res, next) => {
    if (
        err &&
        (
            err.type === 'entity.parse.failed' ||
            (err instanceof SyntaxError && err.status === 400 && 'body' in err)
        )
    ) {
        const reqId = (req.body && req.body.id) ? req.body.id : (req.query && req.query.id) ? req.query.id : 'unknown';
        return res.status(400).json({
            id: reqId,
            result: null,
            error: 'JSON malformado'
        });
    }
    next(err);
});

// Helper para resposta JSON consistente
function jsonResponse(res, id, result, error, status = 200) {
    res.status(status).json({
        id: id !== undefined && id !== null ? id : 'unknown',
        result: result === undefined ? null : result,
        error: error === undefined ? null : error
    });
}

// Simple logging middleware (opcional)
app.use((req, res, next) => {
    console.log(new Date().toISOString(), req.method, req.originalUrl);
    next();
});

// POST /calc/op
app.post('/calc/op', (req, res) => {
    const body = req.body;
    const id = body && body.id ? body.id : 'unknown';

    if (!body || typeof body.op !== 'string' || body.a === undefined || body.b === undefined) {
        return jsonResponse(res, id, null, 'Requisição inválida: esperado {id?, op, a, b}', 400);
    }

    const op = body.op;
    const a = Number(body.a);
    const b = Number(body.b);

    if (!isFinite(a) || !isFinite(b)) {
        return jsonResponse(res, id, null, 'Operandos devem ser números', 400);
    }

    try {
        let r;
        switch (op) {
            case 'add':
                r = a + b;
                break;
            case 'sub':
                r = a - b;
                break;
            case 'mul':
                r = a * b;
                break;
            case 'div':
                if (b === 0) throw new Error('Divisão por zero');
                r = a / b;
                break;
            default:
                throw new Error('Operação inválida: ' + op);
        }
        return jsonResponse(res, id, r, null, 200);
    } catch (e) {
        return jsonResponse(res, id, null, e.message, 400);
    }
});

// POST /calc/expr
app.post('/calc/expr', (req, res) => {
    const body = req.body;
    const id = body && body.id ? body.id : 'unknown';
    if (!body || typeof body.expr !== 'string') {
        return jsonResponse(res, id, null, 'Requisição inválida: esperado {id?, expr}', 400);
    }
    const expr = body.expr;
    try {
        const result = evaluateExpression(expr);
        return jsonResponse(res, id, result, null, 200);
    } catch (e) {
        return jsonResponse(res, id, null, e.message, 400);
    }
});

// --- Expression evaluator (tokenize -> shunting-yard -> RPN evaluate) ---

function evaluateExpression(expr) {
    const s = expr.replace(/\s+/g, '');
    if (s.length === 0) throw new Error('Expressão vazia');

    const tokens = tokenize(s);
    const rpn = shuntingYard(tokens);
    const value = evaluateRPN(rpn);
    return value;
}

function tokenize(s) {
    const tokens = [];
    let i = 0;
    while (i < s.length) {
        const c = s[i];

        // number (supports decimals)
        if (/\d/.test(c) || (c === '.' && i + 1 < s.length && /\d/.test(s[i+1]))) {
            let j = i;
            while (j < s.length && (/[\d\.]/.test(s[j]))) j++;
            const numStr = s.slice(i, j);
            if ((numStr.match(/\./g) || []).length > 1) throw new Error('Número malformado: ' + numStr);
            tokens.push({ type: 'number', value: parseFloat(numStr) });
            i = j;
            continue;
        }

        // operators and parentheses
        if ('+-*/()'.includes(c)) {
            // handle unary + and -: convert to 'u+' / 'u-' tokens when appropriate
            if ((c === '+' || c === '-') ) {
                // unary if at start or after '(' or another operator
                const prev = tokens.length ? tokens[tokens.length - 1] : null;
                if (!prev || (prev.type === 'operator' && prev.value !== ')') || (prev.type === 'paren' && prev.value === '(')) {
                    tokens.push({ type: 'operator', value: (c === '+' ? 'u+' : 'u-') });
                    i++;
                    continue;
                }
            }
            if (c === '(' || c === ')') {
                tokens.push({ type: 'paren', value: c });
            } else {
                tokens.push({ type: 'operator', value: c });
            }
            i++;
            continue;
        }

        // invalid character
        throw new Error('Caracter inválido na expressão: ' + c);
    }
    return tokens;
}

function shuntingYard(tokens) {
    const output = [];
    const ops = [];
    const precedence = { 'u+': 4, 'u-': 4, '*': 3, '/': 3, '+': 2, '-': 2 };
    const rightAssoc = { 'u+': true, 'u-': true };

    tokens.forEach(token => {
        if (token.type === 'number') {
            output.push(token);
        } else if (token.type === 'operator') {
            const o1 = token.value;
            while (ops.length > 0) {
                const top = ops[ops.length - 1];
                if (top.type !== 'operator') break;
                const o2 = top.value;
                const p1 = precedence[o1] || 0;
                const p2 = precedence[o2] || 0;
                if ((rightAssoc[o1] && p1 < p2) || (!rightAssoc[o1] && p1 <= p2)) {
                    output.push(ops.pop());
                } else break;
            }
            ops.push(token);
        } else if (token.type === 'paren') {
            if (token.value === '(') {
                ops.push(token);
            } else {
                // token is ')'
                let foundLeft = false;
                while (ops.length > 0) {
                    const top = ops.pop();
                    if (top.type === 'paren' && top.value === '(') {
                        foundLeft = true;
                        break;
                    } else {
                        output.push(top);
                    }
                }
                if (!foundLeft) throw new Error('Parênteses desbalanceados');
            }
        } else {
            throw new Error('Token desconhecido');
        }
    });

    while (ops.length > 0) {
        const top = ops.pop();
        if (top.type === 'paren') throw new Error('Parênteses desbalanceados');
        output.push(top);
    }

    return output;
}

function evaluateRPN(rpn) {
    const stack = [];
    rpn.forEach(token => {
        if (token.type === 'number') {
            stack.push(token.value);
        } else if (token.type === 'operator') {
            if (token.value === 'u+' || token.value === 'u-') {
                // unary
                if (stack.length < 1) throw new Error('Operador unário sem operando');
                const v = stack.pop();
                stack.push(token.value === 'u-' ? -v : +v);
            } else {
                // binary
                if (stack.length < 2) throw new Error('Operador binário sem operandos suficientes');
                const b = stack.pop();
                const a = stack.pop();
                let r;
                switch (token.value) {
                    case '+': r = a + b; break;
                    case '-': r = a - b; break;
                    case '*': r = a * b; break;
                    case '/':
                        if (b === 0) throw new Error('Divisão por zero');
                        r = a / b; break;
                    default:
                        throw new Error('Operador desconhecido: ' + token.value);
                }
                stack.push(r);
            }
        } else {
            throw new Error('Token inválido na avaliação RPN');
        }
    });

    if (stack.length !== 1) throw new Error('Expressão inválida');
    const result = stack[0];
    if (!isFinite(result)) throw new Error('Resultado não é um número finito');
    return result;
}

// Global error handler (garante JSON em qualquer erro)
app.use((err, req, res, next) => {
    console.error('Unhandled error:', err && err.stack ? err.stack : err);
    const reqId = (req.body && req.body.id) ? req.body.id : 'unknown';
    const status = (err && err.status && Number.isInteger(err.status)) ? err.status : 500;
    res.status(status).json({
        id: reqId,
        result: null,
        error: err && err.message ? err.message : 'Erro interno no servidor'
    });
});

// Start
app.listen(PORT, () => {
    console.log(`Calculator server listening on http://localhost:${PORT}`);
});