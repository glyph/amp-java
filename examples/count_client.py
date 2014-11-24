# python count_client.py

from sys import stdout, exit
from decimal import Decimal
from datetime import datetime
from twisted.internet import reactor
from twisted.internet.protocol import Factory
from twisted.internet.endpoints import TCP4ClientEndpoint
from twisted.protocols import amp
from twisted.python.log import startLogging, err, msg

class Count(amp.Command):
    arguments = [('n', amp.Integer())]
    response = [('oki', amp.Integer()),
                ('oks', amp.String()),
                ('oku', amp.Unicode()),
                ('okb', amp.Boolean()),
                ('okf', amp.Float()),
                ('okd', amp.Decimal()),
                ('okt', amp.DateTime()),
                ('okl1', amp.ListOf(amp.Integer())),
                ('okl2', amp.ListOf(amp.ListOf(amp.String()))),
                ('okla', amp.AmpList([('a', amp.Integer()),
                                      ('b', amp.Unicode())]))]

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

        return { 'oki': 1, 'oks': '2', 'oku': u'3', 'okb': True,
                 'okf': 5.123, 'okd': Decimal('3') / Decimal('4'),
                 'okt': datetime.now(amp.utc),
                 'okl1': [4, 5, 6],
                 'okl2': [['str01','str02'], ['str03','str04','str05']],
                 'okla': [{'a': 7, 'b': u'hello'}, {'a': 9, 'b': u'goodbye'}]
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

if __name__ == '__main__':
    import count_client
    raise SystemExit(count_client.main())
