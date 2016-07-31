import socket
import sys
from binascii import hexlify

hexy = None

def connect(port):
	sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

	# Connect the socket to the port where the server is listening
	server_address = ('localhost', port)
	print >>sys.stderr, 'connecting to %s port %s' % server_address
	sock.connect(server_address)

	amount_received = 0
	i = 1
	while i<10:
		data = sock.recv(16)
		print type(data)
		amount_received += len(data)
		print >>sys.stderr, 'received "%s"' % amount_received
		direct_int = 0
		hexy = hexlify(data)
		timed = int(str("0x" + hexy),0)
		print >>sys.stderr, 'GOT "%s"' % hexlify(data)
		print >>sys.stderr, 'GOT "%d"' % direct_int

		i+=1
		if data != None:
			break
			sock.close()
			return data

	print >>sys.stderr, "Closing socket"
	sock.close()

if __name__ == "__main__":
	connect(int(sys.argv[1]))
