#!/usr/bin/env node

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

// Configuration
const URL = process.argv[2] || "http://dining-philosophers:8080";

// Create server instance
const server = new McpServer({
    name: "dining-philosophers-mcp-server",
    version: "0.1.0"
});

server.tool("get-events", { pattern: z.string().optional() }, async (pattern) => {
    const response = await fetch(`${URL}/events?pattern=${pattern}`);
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

server.prompt(
    "eat-time",
    { philosopherId: z.string() },
    ({ philosopherId }) => ({
        messages: [{
            role: "user",
            content: {
                type: "text",
                text: `You use dining-philosophers get-events to get philosophers informations. For how long did Philosopher ${philosopherId} eat?`
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

async function runServer() {
    try {
        // Set up MCP server
        const transport = new StdioServerTransport();
        await server.connect(transport);
        console.error("Dining-Philosophers MCP Server running on stdio");
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
