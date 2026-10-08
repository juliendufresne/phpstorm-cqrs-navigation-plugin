<?php

namespace App\Command;

use Symfony\Component\Messenger\Attribute\AsMessageHandler;
use Symfony\Component\Messenger\Handler\MessageHandlerInterface;

#[AsMessageHandler]
final class CreateUserHandler
{
    public function __invoke(CreateUser $command): void {}
}

#[AsMessageHandler(handles: DeleteUser::class, method: 'handle')]
final class UserCommands
{
    public function handle($command): void {}

    public function notAHandler(DeleteUser $command): void {}
}

final class RenameUserHandlers
{
    #[AsMessageHandler]
    public function onRename(RenameUser $command): void {}

    #[AsMessageHandler(priority: 10)]
    public function onRenameAgain(RenameUser $command): void {}
}

final class LegacyHandler implements MessageHandlerInterface
{
    public function __invoke(LegacyCommand $command): void {}
}

// Wired in services.yaml, recognized by its name.
final class ArchiveUserHandler
{
    public function __invoke(ArchiveUser $command): void {}
}

// Takes a command, but is not a handler.
final class UserService
{
    public function process(CreateUser $command): void {}

    public function __invoke(CreateUser $command): void {}
}

final class Controller
{
    public function create(): void
    {
        $command = new CreateUser();
        $orphan = new Orphan();
    }
}
