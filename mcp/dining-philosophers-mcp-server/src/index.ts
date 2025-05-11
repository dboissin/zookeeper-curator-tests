#!/usr/bin/env node
import express, { Request, Response } from "express";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { z } from "zod";
import { StreamableHTTPServerTransport } from "@modelcontextprotocol/sdk/server/streamableHttp.js";

interface EnvVariables {
    DINING_PHILOSOPHERS_URL: string;
    PORT: number;
  }

interface Event {
    philosopher: string;
    state: string;
    "start-time": number;
    "end-time": number;
    duration?: number;
}

function getEnv(): EnvVariables {
    return {
        DINING_PHILOSOPHERS_URL: process.env["DINING_PHILOSOPHERS_URL"] || "http://dining-philosophers:8080",
        PORT: parseInt(process.env["PORT"] || "3000"),
    };
}

function buildServer(diningPhilosophersUrl: string) {
    const server = new McpServer({
        name: "dining-philosophers-mcp-server",
        version: "0.2.0"
    });

    server.tool("get-events", {}, async () => {
        const response = await fetch(`${diningPhilosophersUrl}/events`);
        const events = await response.text();
        if (events === null) {
            return {
                content: [
                    {
                        type: "text",
                        text: "No events found",
                    },
                ],
            };
        }
        return {
            content: [
                {
                    type: "text",
                    text: `${events}`,
                },
            ],
        };
    });

    server.tool("eat-duration", { philosopherId: z.string() }, async ({philosopherId}) => {
        const response = await fetch(`${diningPhilosophersUrl}/events`);
        const events = await response.text();
        if (events === null) {
            return {
                content: [
                    {
                        type: "text",
                        text: "No events found",
                    },
                ],
            };
        }
        const duration:number = JSON.parse(events).verifiedEvents
            .filter((event:Event) => event.philosopher.endsWith(philosopherId) && event.state === "Eat")
            .reduce((acc:number, event:Event) => acc + (event["end-time"] - event["start-time"]), 0);
        return {
            content: [
                {
                    type: "text",
                    text: `Philosopher ${philosopherId} ate for ${duration}ms.`,
                },
            ],
        };
    });

    server.prompt(
        "eat-time",
        { philosopherId: z.string() },
        ({ philosopherId }: { philosopherId: string }) => ({
            messages: [{
                role: "user",
                content: {
                    type: "text",
                    text: `You use dining-philosophers eat-duration to get how long did Philosopher ${philosopherId} eat?`
                }
            }]
        })
    );

    server.prompt(
        "list-philosophers",
        {},
        () => ({
            messages: [{
                role: "user",
                content: {
                    type: "text",
                    text: "You use dining-philosophers get-events to list philosophers ids."
                }
            }]
        })
    );
    server.prompt(
        "list-events",
        {},
        () => ({
            messages: [{
                role: "user",
                content: {
                    type: "text",
                    text: "You use dining-philosophers get-events to list events."
                }
            }]
        })
    );
    return server;
}

async function runServer() {
    try {
        const envVars = getEnv();
        const app = express();
        app.use(express.json());

        app.post('/mcp', async (req: Request, res: Response) => {
            // In stateless mode, create a new instance of transport and server for each request
            // to ensure complete isolation. A single instance would cause request ID collisions
            // when multiple clients connect concurrently.

            try {
                const server = buildServer(envVars.DINING_PHILOSOPHERS_URL); 
                const transport: StreamableHTTPServerTransport = new StreamableHTTPServerTransport({
                    sessionIdGenerator: undefined,
                });
                res.on('close', () => {
                    console.log('Request closed');
                    transport.close();
                    server.close();
                });
                await server.connect(transport);
                await transport.handleRequest(req, res, req.body);
            } catch (error) {
                console.error('Error handling MCP request:', error);
                if (!res.headersSent) {
                    res.status(500).json({
                        jsonrpc: '2.0',
                        error: {
                        code: -32603,
                        message: 'Internal server error',
                        },
                        id: null,
                    });
                }
            }
        });

        app.get('/mcp', async (req: Request, res: Response) => {
            console.log('Received GET MCP request');
            res.writeHead(405).end(JSON.stringify({
                jsonrpc: "2.0",
                error: {
                    code: -32000,
                    message: "Method not allowed."
                },
                id: null
            }));
        });

        app.delete('/mcp', async (req: Request, res: Response) => {
            console.log('Received DELETE MCP request');
            res.writeHead(405).end(JSON.stringify({
                jsonrpc: "2.0",
                error: {
                    code: -32000,
                    message: "Method not allowed."
                },
                id: null
            }));
        });

        // Start the server
        app.listen(envVars.PORT, () => {
        console.log(`MCP Stateless Streamable HTTP Server listening on port ${envVars.PORT}`);
        });

    } catch (error) {
        const err = error as Error;
        console.error("[Dining-Philosophers Fatal] Server initialization failed");
        console.error(`[Dining-Philosophers Fatal] Error: ${err.name}: ${err.message}`);
        console.error(`[Dining-Philosophers Fatal] Stack: ${err.stack}`);
        process.exit(1);
    }
}

// Handle process termination
process.on('SIGINT', async () => {
    process.exit(0);
});

process.on('SIGTERM', async () => {
    process.exit(0);
});

runServer();
