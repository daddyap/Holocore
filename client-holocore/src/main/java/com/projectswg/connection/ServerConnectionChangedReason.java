package com.projectswg.connection;

public enum ServerConnectionChangedReason {
	NONE,
	CLIENT_DISCONNECT,
	SOCKET_CLOSED,
	CONNECT_TIMEOUT,
	INVALID_PROTOCOL,
	BROKEN_PIPE,
	CONNECTION_RESET,
	CONNECTION_REFUSED,
	ADDR_IN_USE,
	NO_ROUTE_TO_HOST,
	OTHER_SIDE_TERMINATED,
	UNKNOWN
}