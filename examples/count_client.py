# python count_client.py

if __name__ == '__main__':
    import count_client
    raise SystemExit(count_client.main())

from decimal import Decimal
from datetime import datetime
from sys import stdout, exit
from twisted.python.log import startLogging, err, msg
from twisted.protocols import amp
from twisted.internet import reactor
from twisted.internet.protocol import Factory
from twisted.internet.endpoints import TCP4ClientEndpoint

class Count(amp.Command):
    arguments = [('n', amp.Integer())]
    response = [('oki', amp.Integer()),
                ('oks', amp.String()),
                ('oku', amp.Unicode()),
                ('okb', amp.Boolean()),
                ('okf', amp.Float()),
                ('okd', amp.Decimal()),
                ('okt', amp.DateTime()),
                ('okl', amp.ListOf(amp.ListOf(amp.String())))]

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

        return { 'oki': 1, 'oks': '2', 'oku': '3', 'okb': True,
                 'okf': 5.123, 'okd': Decimal('3') / Decimal('4'),
                 'okt': datetime.now(amp.utc),
                 'okl': [['str01','str02'], ['str03','str04','str05']],
             }

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
