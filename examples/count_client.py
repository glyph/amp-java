# python count_client.py

if __name__ == '__main__':
    import count_client
    raise SystemExit(count_client.main())

from sys import stdout, exit
from twisted.python.log import startLogging, err, msg
from twisted.protocols import amp
from twisted.internet import reactor
from twisted.internet.protocol import Factory
from twisted.internet.endpoints import TCP4ClientEndpoint

class Count(amp.Command):
    arguments = [('n', amp.Integer())]
    response = [('ok', amp.Boolean())]

class Counter(amp.AMP):
    @Count.responder
    def count(self, n):
        print 'received:', n
        n += 1

        maxcount = 10
        if (n < maxcount):
            print 'sending:', n
            d = self.callRemote(Count, n=n)
            d.addCallback(lambda resp: msg("response:", resp))
            d.addErrback(err, 'callRemote failed')
        else:
            reactor.stop()

        return {'ok': True}

    def connectionLost(self, reason):
        print 'Count limit reached, exiting...'

def connect():
    endpoint = TCP4ClientEndpoint(reactor, '127.0.0.1', 7113)
    factory = Factory()
    factory.protocol = Counter
    return endpoint.connect(factory)

def main():
    startLogging(stdout)

    d = connect()
    d.addErrback(err, 'connection failed')
    d.addCallback(lambda p: p.callRemote(Count, n=1))
    d.addErrback(err, 'call failed')

    reactor.run()
