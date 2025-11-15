package main

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"sync"
	"time"
)

// MCPRequest represents an incoming MCP request
type MCPRequest struct {
	JSONRPC string                 `json:"jsonrpc"`
	ID      interface{}            `json:"id"`
	Method  string                 `json:"method"`
	Params  map[string]interface{} `json:"params,omitempty"`
}

// MCPResponse represents an MCP response
type MCPResponse struct {
	JSONRPC string      `json:"jsonrpc"`
	ID      interface{} `json:"id"`
	Result  interface{} `json:"result,omitempty"`
	Error   interface{} `json:"error,omitempty"`
}

// MCPBridge manages the stdio communication with the MCP server
type MCPBridge struct {
	cmd             *exec.Cmd
	stdin           io.WriteCloser
	stdout          io.ReadCloser
	stderr          io.ReadCloser
	writeMu         sync.Mutex
	pendingRequests map[string]chan MCPResponse
	pendingMu       sync.RWMutex
	ready           bool
	nextID          int
	idMu            sync.Mutex
}

// NewMCPBridge creates and starts the MCP server subprocess
func NewMCPBridge() (*MCPBridge, error) {
	// Use the github-mcp-server binary
	cmd := exec.Command("/server/github-mcp-server", "stdio")
	
	// Pass through environment variables (especially GITHUB_PERSONAL_ACCESS_TOKEN)
	cmd.Env = os.Environ()

	// Setup pipes
	stdin, err := cmd.StdinPipe()
	if err != nil {
		return nil, fmt.Errorf("failed to create stdin pipe: %w", err)
	}

	stdout, err := cmd.StdoutPipe()
	if err != nil {
		return nil, fmt.Errorf("failed to create stdout pipe: %w", err)
	}

	stderr, err := cmd.StderrPipe()
	if err != nil {
		return nil, fmt.Errorf("failed to create stderr pipe: %w", err)
	}

	// Start the MCP server
	if err := cmd.Start(); err != nil {
		return nil, fmt.Errorf("failed to start MCP server: %w", err)
	}

	bridge := &MCPBridge{
		cmd:             cmd,
		stdin:           stdin,
		stdout:          stdout,
		stderr:          stderr,
		pendingRequests: make(map[string]chan MCPResponse),
		ready:           false,
		nextID:          1,
	}

	// Start monitoring stderr and stdout
	go bridge.monitorStderr()
	go bridge.monitorStdout()

	// Send initialize request
	if err := bridge.initialize(); err != nil {
		bridge.Close()
		return nil, fmt.Errorf("failed to initialize MCP server: %w", err)
	}

	bridge.ready = true
	log.Println("GitHub MCP Server initialized and ready")
	return bridge, nil
}

// initialize sends the MCP initialize request
func (b *MCPBridge) initialize() error {
	initReq := MCPRequest{
		JSONRPC: "2.0",
		ID:      0,
		Method:  "initialize",
		Params: map[string]interface{}{
			"protocolVersion": "2024-11-05",
			"capabilities":    map[string]interface{}{},
			"clientInfo": map[string]interface{}{
				"name":    "github-mcp-http-wrapper",
				"version": "1.0.0",
			},
		},
	}

	// Send initialize request
	reqJSON, err := json.Marshal(initReq)
	if err != nil {
		return fmt.Errorf("failed to marshal initialize request: %w", err)
	}

	b.writeMu.Lock()
	_, err = b.stdin.Write(append(reqJSON, '\n'))
	b.writeMu.Unlock()
	if err != nil {
		return fmt.Errorf("failed to send initialize request: %w", err)
	}

	// Wait for initialize response with timeout
	time.Sleep(3 * time.Second)
	
	// Send initialized notification
	notifyReq := map[string]interface{}{
		"jsonrpc": "2.0",
		"method":  "notifications/initialized",
	}
	
	notifyJSON, err := json.Marshal(notifyReq)
	if err != nil {
		return fmt.Errorf("failed to marshal initialized notification: %w", err)
	}

	b.writeMu.Lock()
	_, err = b.stdin.Write(append(notifyJSON, '\n'))
	b.writeMu.Unlock()
	if err != nil {
		return fmt.Errorf("failed to send initialized notification: %w", err)
	}

	return nil
}

// monitorStderr logs stderr output from the MCP server
func (b *MCPBridge) monitorStderr() {
	scanner := bufio.NewScanner(b.stderr)
	for scanner.Scan() {
		log.Printf("[MCP Server] %s", scanner.Text())
	}
	if err := scanner.Err(); err != nil {
		log.Printf("Error reading stderr: %v", err)
	}
}

// monitorStdout reads responses from the MCP server and routes them to pending requests
func (b *MCPBridge) monitorStdout() {
	scanner := bufio.NewScanner(b.stdout)
	for scanner.Scan() {
		line := scanner.Bytes()
		
		// Parse the response
		var resp MCPResponse
		if err := json.Unmarshal(line, &resp); err != nil {
			log.Printf("Error parsing MCP response: %v (line: %s)", err, string(line))
			continue
		}

		// Log notifications (responses without ID)
		if resp.ID == nil {
			log.Printf("[MCP Notification] %s", string(line))
			continue
		}

		// Convert ID to string for map lookup
		idStr := fmt.Sprintf("%v", resp.ID)
		
		// Route response to waiting request
		b.pendingMu.RLock()
		respChan, exists := b.pendingRequests[idStr]
		b.pendingMu.RUnlock()

		if exists {
			select {
			case respChan <- resp:
			case <-time.After(1 * time.Second):
				log.Printf("Warning: Response channel full or closed for ID %s", idStr)
			}
		} else {
			log.Printf("Warning: Received response for unknown request ID: %s", idStr)
		}
	}
	
	if err := scanner.Err(); err != nil {
		log.Printf("Error reading stdout: %v", err)
	}
}

// getNextID returns the next request ID
func (b *MCPBridge) getNextID() int {
	b.idMu.Lock()
	defer b.idMu.Unlock()
	id := b.nextID
	b.nextID++
	return id
}

// SendRequest sends a request to the MCP server and returns the response
func (b *MCPBridge) SendRequest(req MCPRequest) (MCPResponse, error) {
	if !b.ready {
		return MCPResponse{}, fmt.Errorf("MCP server not ready")
	}

	// Assign ID if not present
	if req.ID == nil {
		req.ID = b.getNextID()
	}
	
	idStr := fmt.Sprintf("%v", req.ID)

	// Create response channel
	respChan := make(chan MCPResponse, 1)
	
	b.pendingMu.Lock()
	b.pendingRequests[idStr] = respChan
	b.pendingMu.Unlock()

	defer func() {
		b.pendingMu.Lock()
		delete(b.pendingRequests, idStr)
		b.pendingMu.Unlock()
		close(respChan)
	}()

	// Marshal request to JSON
	reqJSON, err := json.Marshal(req)
	if err != nil {
		return MCPResponse{}, fmt.Errorf("failed to marshal request: %w", err)
	}

	// Send request with newline
	b.writeMu.Lock()
	_, err = b.stdin.Write(append(reqJSON, '\n'))
	b.writeMu.Unlock()
	if err != nil {
		return MCPResponse{}, fmt.Errorf("failed to write request: %w", err)
	}

	// Wait for response with timeout
	select {
	case resp := <-respChan:
		return resp, nil
	case <-time.After(30 * time.Second):
		return MCPResponse{}, fmt.Errorf("request timeout")
	}
}

// Close terminates the MCP server subprocess
func (b *MCPBridge) Close() error {
	if b.stdin != nil {
		b.stdin.Close()
	}

	if b.cmd != nil && b.cmd.Process != nil {
		if err := b.cmd.Process.Kill(); err != nil {
			return err
		}
		b.cmd.Wait()
	}

	return nil
}

var bridge *MCPBridge

func main() {
	// Create the MCP bridge
	var err error
	bridge, err = NewMCPBridge()
	if err != nil {
		log.Fatalf("Failed to create MCP bridge: %v", err)
	}
	defer bridge.Close()

	// Setup HTTP server
	http.HandleFunc("/health", healthHandler)
	http.HandleFunc("/mcp", mcpHandler)
	http.HandleFunc("/", rootHandler)

	port := os.Getenv("PORT")
	if port == "" {
		port = "3000"
	}

	log.Printf("Starting HTTP wrapper on port %s", port)
	server := &http.Server{
		Addr:         ":" + port,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 30 * time.Second,
	}

	// Graceful shutdown
	go func() {
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("HTTP server error: %v", err)
		}
	}()

	log.Println("HTTP wrapper ready - GitHub MCP Server accessible via HTTP")

	// Wait for interrupt signal
	select {}
}

func healthHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]interface{}{
		"status": "healthy",
		"ready":  bridge.ready,
	})
}

func rootHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	json.NewEncoder(w).Encode(map[string]interface{}{
		"service": "GitHub MCP Server HTTP Wrapper",
		"version": "1.0.0",
		"ready":   bridge.ready,
	})
}

func mcpHandler(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		http.Error(w, "Method not allowed", http.StatusMethodNotAllowed)
		return
	}

	// Parse request
	var req MCPRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, fmt.Sprintf("Invalid request: %v", err), http.StatusBadRequest)
		return
	}

	// Forward to MCP server
	ctx, cancel := context.WithTimeout(r.Context(), 25*time.Second)
	defer cancel()

	respChan := make(chan MCPResponse, 1)
	errChan := make(chan error, 1)

	go func() {
		resp, err := bridge.SendRequest(req)
		if err != nil {
			errChan <- err
			return
		}
		respChan <- resp
	}()

	select {
	case <-ctx.Done():
		http.Error(w, "Request timeout", http.StatusGatewayTimeout)
		return
	case err := <-errChan:
		log.Printf("Error processing MCP request: %v", err)
		http.Error(w, fmt.Sprintf("MCP server error: %v", err), http.StatusInternalServerError)
		return
	case resp := <-respChan:
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		json.NewEncoder(w).Encode(resp)
	}
}
