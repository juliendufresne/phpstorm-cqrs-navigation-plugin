<?php

namespace App\Event;

use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\EventDispatcher\EventSubscriberInterface;

#[AsEventListener]
final class SendWelcomeEmail
{
    public function __invoke(UserCreated $event): void {}
}

final class Audit
{
    #[AsEventListener(event: UserCreated::class)]
    public function log($event): void {}
}

final class UserSubscriber implements EventSubscriberInterface
{
    public static function getSubscribedEvents(): array
    {
        return [
            UserCreated::class => 'onCreated',
            UserDeleted::class => [['first', 10], ['second']],
        ];
    }

    public function onCreated(UserCreated $event): void {}

    public function first(UserDeleted $event): void {}

    public function second(UserDeleted $event): void {}
}
