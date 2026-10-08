<?php

namespace Symfony\Component\Messenger\Attribute {
    #[\Attribute(\Attribute::TARGET_CLASS | \Attribute::TARGET_METHOD | \Attribute::IS_REPEATABLE)]
    class AsMessageHandler
    {
        public function __construct(
            public ?string $bus = null,
            public ?string $fromTransport = null,
            public ?string $handles = null,
            public ?string $method = null,
            public int $priority = 0,
        ) {}
    }
}

namespace Symfony\Component\Messenger\Handler {
    interface MessageHandlerInterface {}
}

namespace Symfony\Component\EventDispatcher\Attribute {
    #[\Attribute(\Attribute::TARGET_CLASS | \Attribute::TARGET_METHOD | \Attribute::IS_REPEATABLE)]
    class AsEventListener
    {
        public function __construct(
            public ?string $event = null,
            public ?string $method = null,
            public int $priority = 0,
            public ?string $dispatcher = null,
        ) {}
    }
}

namespace Symfony\Component\EventDispatcher {
    interface EventSubscriberInterface
    {
        public static function getSubscribedEvents(): array;
    }
}
